/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.cvsSupport2.connections;

import com.intellij.CvsBundle;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.regex.Matcher;
import java.util.regex.Pattern;


/**
 * author: lesya
 */
public final class CvsRootParser {

  //:pserver;username=lesya;password=password111;hostname=hostname111;port=port111;proxy=proxy111;proxyport=proxyport111;tunnel=tumnnel111;proxyuser=proxyuser111;proxypassword=proxypassword111:c:/RepositoryPath

  @NonNls private static final String PATTERN_STR = "^((.*?{0,1}(:.*?){0,1})@){0,1}([a-zA-Z0-9\\._-]+)(:(\\d*){0,1}){0,1}(.+)$";
  private static final Pattern ourPattern = Pattern.compile(PATTERN_STR);

  private static final int GROUP_USER_NAME_AND_PWD = 2;
  private static final int GROUP_HOST = 4;
  private static final int GROUP_PORT = 5;
  private static final int GROUP_REPOSITORY = 7;

  public CvsMethod METHOD;
  public String USER_NAME;
  public String HOST;
  public String REPOSITORY;
  public String PROXY_HOST;
  public String PROXY_PORT;
  public String PORT;
  public String PASSWORD;

  @NonNls private static final String USERNAME_FIELD_NAME = "username";
  @NonNls private static final String PASSWORD_FIELD_NAME = "password";
  @NonNls private static final String HOSTNAME__FIELD_NAME = "hostname";
  @NonNls private static final String PROXY_FIELD_NAME = "proxy";
  @NonNls private static final String PROXYPORT_FIELD_NAME = "proxyport";
  @NonNls private static final String PORT_FIELD_NAME = "port";


  @NotNull public static CvsRootParser valueOf(String str, boolean check) {

    CvsRootParser result = new CvsRootParser();

    if (!StringUtil.startsWithChar(str, ':')) {
      if (check) {
        throw new CvsRootException(CvsBundle.message("message.error.invalid.cvs.root", str));
      } else {
        result.METHOD = CvsMethod.LOCAL_METHOD;
        result.REPOSITORY = str;
        return result;
      }
    }

    @NonNls String local2 = ":local:";

    if (str.startsWith(local2)){
      result.METHOD = CvsMethod.LOCAL_METHOD;
      result.REPOSITORY = str.substring(local2.length());
      return result;
    }

    String suffix = result.extractMethod(str, result, check);

    if (CvsMethod.LOCAL_METHOD.equals(result.METHOD)) {
      result.REPOSITORY = suffix;
      skipTrailingRepositorySlash(result);
    }
    else {
      if (result.HOST != null && result.HOST.length() > 0 && result.USER_NAME != null && result.USER_NAME.length() > 0) {
        result.REPOSITORY = suffix.trim();
      }
      else if (suffix.contains("@") || suffix.contains(":")){
        Matcher matcher = ourPattern.matcher(suffix);

        if (matcher.matches()) {
          extractUserNameAndPassword(matcher, result);
          extractHostAndPort(matcher, result);
          extractRepository(matcher, result);
        }
        else {
          if (check) {
            throw new IllegalArgumentException(CvsBundle.message("error.message.wrong.remote.repository", str));
          }
          else {
            result.REPOSITORY = suffix;
          }
        }
      } else {
        result.REPOSITORY = suffix;
      }
    }

    return result;
  }

  private static void extractRepository(Matcher matcher, CvsRootParser cvsRoot) {
    cvsRoot.REPOSITORY = matcher.group(GROUP_REPOSITORY);
    skipTrailingRepositorySlash(cvsRoot);

  }

  private static void skipTrailingRepositorySlash(CvsRootParser cvsRoot) {
    if (StringUtil.endsWithChar(cvsRoot.REPOSITORY, '/')) {
      cvsRoot.REPOSITORY =
      cvsRoot.REPOSITORY.substring(0, cvsRoot.REPOSITORY.length() - 1);
    }
  }

  private static void extractHostAndPort(Matcher matcher, CvsRootParser cvsRoot) {
    String host = matcher.group(GROUP_HOST);
    String port = matcher.group(GROUP_PORT);

    if (port != null) {
      cvsRoot.HOST = host + port;
    }
    else {
      if (StringUtil.endsWithChar(host, ':')) {
        host = host.substring(0, host.length() - 1);
      }
      cvsRoot.HOST = host;
    }

  }

  private static void extractUserNameAndPassword(Matcher matcher, CvsRootParser cvsRoot) {
    String userNameAndPwd = matcher.group(GROUP_USER_NAME_AND_PWD);
    if (userNameAndPwd != null && cvsRoot.USER_NAME.length() == 0) {
      cvsRoot.USER_NAME = userNameAndPwd;
    }
  }

  private String tryToCutMethod(CvsMethod method, String cvsRoot) {
    String methodentry = methodEntry(method.getName());
    if (cvsRoot.startsWith(methodentry)) {
      return cvsRoot.substring(methodentry.length());
    }
    if (!method.supportsProxyConnection()) {
      return null;
    }

    String proxyBegin = ":" + method.getName() + ";";

    if (!cvsRoot.startsWith(proxyBegin)) {
      return null;
    }

    String tail = cvsRoot.substring(proxyBegin.length() - 1);

    int endOfProxySettings = tail.indexOf(':');
    if (endOfProxySettings == -1){
      return null;
    }

    String proxySettings = tail.substring(0, endOfProxySettings);

    final String[] paramValueStrings = proxySettings.split(";");

    for (String paramValueString : paramValueStrings) {
      final int eqIndex = paramValueString.indexOf("=");
      if (eqIndex >= 0) {
        setValue(paramValueString.substring(0, eqIndex), paramValueString.substring(eqIndex + 1));
      }
    }

    return tail.substring(endOfProxySettings + 1);
  }

  private void setValue(final String paramName, final String paramValue) {
    if (paramName.length() == 0 || paramValue.length() == 0) return;

    if (USERNAME_FIELD_NAME.equals(paramName)){
      USER_NAME = paramValue;
    }
    else if(PASSWORD_FIELD_NAME.equals(paramName)){
      PASSWORD = paramValue;
    }
    else if (HOSTNAME__FIELD_NAME.equals(paramName)){
      HOST=paramValue;
    }
    else if (PROXY_FIELD_NAME.equals(paramName)){
      PROXY_HOST= paramValue;
    }
    else if (PROXYPORT_FIELD_NAME.equals(paramName)){
      PROXY_PORT= paramValue;
    }
    else if (PORT_FIELD_NAME.equals(paramName)){
      PORT= paramValue;
    }

  }

  private static String methodEntry(String method) {
    return ":" + method + ":";
  }


  private String extractMethod(String str, CvsRootParser cvsRoot, boolean check) {
    for (CvsMethod cvsMethod : CvsMethod.AVAILABLE_METHODS) {
      String tail = tryToCutMethod(cvsMethod, str);
      if (tail != null) {
        cvsRoot.METHOD = cvsMethod;
        return tail;
      }
    }
    if (check) {
      throw new CvsRootException(CvsBundle.message("message.error.invalid.cvs.root", str));
    }
    cvsRoot.METHOD = CvsMethod.AVAILABLE_METHODS[0];
    if (!StringUtil.startsWithChar(str, ':')) return str;
    int nextSep = str.indexOf(":", 1);
    if (nextSep < 0) return str;
    return str.substring(nextSep + 1);
  }

  private CvsRootParser() {
    METHOD = null;
    USER_NAME = "";
    HOST = "";
    REPOSITORY = "";
    PORT = null;
    PASSWORD = null;
  }

}
