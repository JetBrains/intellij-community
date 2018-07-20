/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.plugins.github.api;

import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.vfs.CharsetToolkit;
import org.apache.http.*;
import org.apache.http.client.methods.*;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.github.api.data.GithubErrorMessage;
import org.jetbrains.plugins.github.authentication.accounts.GithubAccount;
import org.jetbrains.plugins.github.exceptions.*;
import org.jetbrains.plugins.github.util.GithubAuthData;
import org.jetbrains.plugins.github.util.GithubUrlUtil;
import org.jetbrains.plugins.github.util.GithubUtil;

import javax.net.ssl.SSLHandshakeException;
import java.awt.*;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.security.cert.CertificateException;
import java.util.*;
import java.util.List;

import static org.jetbrains.plugins.github.api.GithubApiUtil.fromJson;

public class GithubConnection {
  private static final Logger LOG = GithubUtil.LOG;

  // nullable for backwards compatibility
  @Nullable private GithubAccount myAccount;
  @NotNull private final String myApiURL;
  @NotNull private final CloseableHttpClient myClient;
  private final boolean myReusable;

  private volatile HttpUriRequest myRequest;
  private volatile boolean myAborted;

  public GithubConnection(@NotNull GithubAuthData auth, boolean reusable) {
    myApiURL = GithubUrlUtil.getApiUrl(auth.getHost());
    myClient = new GithubConnectionBuilder(auth, myApiURL).createClient();
    myReusable = reusable;
  }

  private enum HttpVerb {
    GET, POST, DELETE, HEAD, PATCH
  }

  @Nullable
  public JsonElement getRequest(@NotNull String path,
                                @NotNull Header... headers) throws IOException {
    return request(path, null, Arrays.asList(headers), HttpVerb.GET).getJsonElement();
  }

  @Nullable
  public JsonElement postRequest(@NotNull String path,
                                 @Nullable String requestBody,
                                 @NotNull Header... headers) throws IOException {
    return request(path, requestBody, Arrays.asList(headers), HttpVerb.POST).getJsonElement();
  }

  @Nullable
  public JsonElement patchRequest(@NotNull String path,
                                  @Nullable String requestBody,
                                  @NotNull Header... headers) throws IOException {
    return request(path, requestBody, Arrays.asList(headers), HttpVerb.PATCH).getJsonElement();
  }

  @Nullable
  public JsonElement deleteRequest(@NotNull String path,
                                   @NotNull Header... headers) throws IOException {
    return request(path, null, Arrays.asList(headers), HttpVerb.DELETE).getJsonElement();
  }

  @NotNull
  public Header[] headRequest(@NotNull String path,
                              @NotNull Header... headers) throws IOException {
    return request(path, null, Arrays.asList(headers), HttpVerb.HEAD).getHeaders();
  }

  @Nullable
  public GithubAccount getAccount() {
    return myAccount;
  }

  public void setAccount(@NotNull GithubAccount account) {
    myAccount = account;
  }

  @NotNull
  String getApiURL() {
    return myApiURL;
  }

  public void abort() {
    if (myAborted) return;
    myAborted = true;

    HttpUriRequest request = myRequest;
    if (request != null) request.abort();
  }

  public void close() throws IOException {
    myClient.close();
  }

  @NotNull
  private ResponsePage request(@NotNull String path,
                               @Nullable String requestBody,
                               @NotNull Collection<Header> headers,
                               @NotNull HttpVerb verb) throws IOException {
    return doRequest(myApiURL + path, requestBody, headers, verb);
  }

  @NotNull
  private ResponsePage doRequest(@NotNull String uri,
                                 @Nullable String requestBody,
                                 @NotNull Collection<Header> headers,
                                 @NotNull HttpVerb verb) throws IOException {
    if (myAborted) throw new GithubOperationCanceledException();

    if (EventQueue.isDispatchThread() && !ApplicationManager.getApplication().isUnitTestMode()) {
      LOG.warn("Network operation in EDT"); // TODO: fix
    }

    CloseableHttpResponse response = null;
    try {
      response = doREST(uri, requestBody, headers, verb);

      if (myAborted) throw new GithubOperationCanceledException();

      checkStatusCode(response, requestBody);

      HttpEntity entity = response.getEntity();
      if (entity == null) {
        return createResponse(response);
      }

      JsonElement ret = parseResponse(entity.getContent());
      if (ret.isJsonNull()) {
        return createResponse(response);
      }

      String nextPage = null;
      Header pageHeader = response.getFirstHeader("Link");
      if (pageHeader != null) {
        for (HeaderElement element : pageHeader.getElements()) {
          NameValuePair rel = element.getParameterByName("rel");
          if (rel != null && "next".equals(rel.getValue())) {
            String urlString = element.toString();
            int begin = urlString.indexOf('<');
            int end = urlString.lastIndexOf('>');
            if (begin == -1 || end == -1) {
              LOG.error("Invalid 'Link' header", "{" + pageHeader.toString() + "}");
              break;
            }

            nextPage = urlString.substring(begin + 1, end);
            break;
          }
        }
      }

      return createResponse(ret, nextPage, response);
    }
    catch (SSLHandshakeException e) { // User canceled operation from CertificateManager
      if (e.getCause() instanceof CertificateException) {
        LOG.info("Host SSL certificate is not trusted", e);
        throw new GithubOperationCanceledException("Host SSL certificate is not trusted", e);
      }
      throw e;
    }
    catch (IOException e) {
      if (myAborted) throw new GithubOperationCanceledException("Operation canceled", e);
      throw e;
    }
    finally {
      myRequest = null;
      if (response != null) {
        response.close();
      }
      if (!myReusable) {
        myClient.close();
      }
    }
  }

  @NotNull
  private CloseableHttpResponse doREST(@NotNull final String uri,
                                       @Nullable final String requestBody,
                                       @NotNull final Collection<Header> headers,
                                       @NotNull final HttpVerb verb) throws IOException {
    HttpRequestBase request;
    switch (verb) {
      case POST:
        request = new HttpPost(uri);
        if (requestBody != null) {
          ((HttpPost)request).setEntity(new StringEntity(requestBody, ContentType.APPLICATION_JSON));
        }
        break;
      case PATCH:
        request = new HttpPatch(uri);
        if (requestBody != null) {
          ((HttpPatch)request).setEntity(new StringEntity(requestBody, ContentType.APPLICATION_JSON));
        }
        break;
      case GET:
        request = new HttpGet(uri);
        break;
      case DELETE:
        request = new HttpDelete(uri);
        break;
      case HEAD:
        request = new HttpHead(uri);
        break;
      default:
        throw new IllegalStateException("Unknown HttpVerb: " + verb.toString());
    }

    for (Header header : headers) {
      request.addHeader(header);
    }

    myRequest = request;
    return myClient.execute(request);
  }

  private static void checkStatusCode(@NotNull CloseableHttpResponse response, @Nullable String body) throws IOException {
    int code = response.getStatusLine().getStatusCode();
    switch (code) {
      case HttpStatus.SC_OK:
      case HttpStatus.SC_CREATED:
      case HttpStatus.SC_ACCEPTED:
      case HttpStatus.SC_NO_CONTENT:
        return;
      case HttpStatus.SC_UNAUTHORIZED:
      case HttpStatus.SC_PAYMENT_REQUIRED:
      case HttpStatus.SC_FORBIDDEN:
        //noinspection ThrowableResultOfMethodCallIgnored
        GithubStatusCodeException error = getStatusCodeException(response);

        Header headerOTP = response.getFirstHeader("X-GitHub-OTP");
        if (headerOTP != null) {
          for (HeaderElement element : headerOTP.getElements()) {
            if ("required".equals(element.getName())) {
              throw new GithubTwoFactorAuthenticationException(error.getMessage());
            }
          }
        }

        if (error.getError() != null && error.getError().containsReasonMessage("API rate limit exceeded")) {
          throw new GithubRateLimitExceededException(error.getMessage());
        }

        throw new GithubAuthenticationException("Request response: " + error.getMessage());
      case HttpStatus.SC_BAD_REQUEST:
      case HttpStatus.SC_UNPROCESSABLE_ENTITY:
        LOG.info("body message:" + body);
        throw getStatusCodeException(response);
      default:
        throw getStatusCodeException(response);
    }
  }

  @NotNull
  private static GithubStatusCodeException getStatusCodeException(@NotNull CloseableHttpResponse response) {
    StatusLine statusLine = response.getStatusLine();
    int statusCode = statusLine.getStatusCode();
    String reason = statusCode + " " + statusLine.getReasonPhrase();
    try {
      HttpEntity entity = response.getEntity();
      if (entity != null) {
        GithubErrorMessage error = fromJson(parseResponse(entity.getContent()), GithubErrorMessage.class);
        String message = reason + " - " + error.getMessage();
        return new GithubStatusCodeException(message, error, statusCode);
      }
    }
    catch (IOException e) {
      LOG.info(e);
    }
    return new GithubStatusCodeException(reason, statusCode);
  }

  @NotNull
  private static JsonElement parseResponse(@NotNull InputStream githubResponse) throws IOException {
    Reader reader = new InputStreamReader(githubResponse, CharsetToolkit.UTF8_CHARSET);
    try {
      return new JsonParser().parse(reader);
    }
    catch (JsonParseException jse) {
      throw new GithubJsonException("Couldn't parse GitHub response", jse);
    }
    finally {
      reader.close();
    }
  }

  public static abstract class PagedRequestBase<T> implements PagedRequest<T> {
    @NotNull private final String myPath;
    @NotNull private final Collection<Header> myHeaders;

    private boolean myFirstRequest = true;
    @Nullable private String myNextPage;

    public PagedRequestBase(@NotNull String path, @NotNull Header... headers) {
      myPath = path;
      myHeaders = Arrays.asList(headers);
    }

    @NotNull
    public List<T> next(@NotNull GithubConnection connection) throws IOException {
      String url;
      if (myFirstRequest) {
        url = connection.getApiURL() + myPath;
        myFirstRequest = false;
      }
      else {
        if (myNextPage == null) throw new NoSuchElementException();
        url = myNextPage;
        myNextPage = null;
      }

      ResponsePage response = connection.doRequest(url, null, myHeaders, HttpVerb.GET);
      myNextPage = response.getNextPage();

      if (response.getJsonElement() == null) {
        throw new GithubConfusingException("Empty response");
      }

      return parse(response.getJsonElement());
    }

    public boolean hasNext() {
      return myFirstRequest || myNextPage != null;
    }

    protected abstract List<T> parse(@NotNull JsonElement response) throws IOException;
  }

  public static class ArrayPagedRequest<T> extends PagedRequestBase<T> {
    @NotNull private final Class<? extends T[]> myTypeArray;

    public ArrayPagedRequest(@NotNull String path,
                             @NotNull Class<? extends T[]> typeArray,
                             @NotNull Header... headers) {
      super(path, headers);
      myTypeArray = typeArray;
    }

    @Override
    protected List<T> parse(@NotNull JsonElement response) throws IOException {
      if (!response.isJsonArray()) {
        throw new GithubJsonException("Wrong json type: expected JsonArray", new Exception(response.toString()));
      }

      T[] result = fromJson(response.getAsJsonArray(), myTypeArray);
      return Arrays.asList(result);
    }
  }

  public static class SingleValuePagedRequest<T> extends PagedRequestBase<T> {
    @NotNull private final Class<? extends T> myType;

    public SingleValuePagedRequest(@NotNull String path,
                                   @NotNull Class<? extends T> type,
                                   @NotNull Header... headers) {
      super(path, headers);
      myType = type;
    }

    @Override
    protected List<T> parse(@NotNull JsonElement response) throws IOException {
      T result = fromJson(response, myType);
      return Collections.singletonList(result);
    }
  }

  private ResponsePage createResponse(@NotNull CloseableHttpResponse response) throws GithubOperationCanceledException {
    if (myAborted) throw new GithubOperationCanceledException();

    return new ResponsePage(null, null, response.getAllHeaders());
  }

  private ResponsePage createResponse(@NotNull JsonElement ret, @Nullable String path, @NotNull CloseableHttpResponse response)
    throws GithubOperationCanceledException {
    if (myAborted) throw new GithubOperationCanceledException();

    return new ResponsePage(ret, path, response.getAllHeaders());
  }

  private static class ResponsePage {
    @Nullable private final JsonElement myResponse;
    @Nullable private final String myNextPage;
    @NotNull private final Header[] myHeaders;

    public ResponsePage(@Nullable JsonElement response, @Nullable String next, @NotNull Header[] headers) {
      myResponse = response;
      myNextPage = next;
      myHeaders = headers;
    }

    @Nullable
    public JsonElement getJsonElement() {
      return myResponse;
    }

    @Nullable
    public String getNextPage() {
      return myNextPage;
    }

    @NotNull
    public Header[] getHeaders() {
      return myHeaders;
    }
  }

  public interface PagedRequest<T> {
    @NotNull
    List<T> next(@NotNull GithubConnection connection) throws IOException;

    boolean hasNext();

    @NotNull
    default List<T> getAll(@NotNull GithubConnection connection) throws IOException {
      List<T> result = new ArrayList<>();
      while (hasNext()) {
        result.addAll(next(connection));
      }
      return result;
    }
  }
}
