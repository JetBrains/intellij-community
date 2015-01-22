package org.jetbrains.io.jsonRpc;

import com.google.gson.*;
import com.google.gson.internal.Streams;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.AsyncResult;
import com.intellij.openapi.util.NotNullLazyValue;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Ref;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.Consumer;
import com.intellij.util.SmartList;
import com.intellij.util.text.CharSequenceBackedByArray;
import gnu.trove.THashMap;
import gnu.trove.TIntArrayList;
import gnu.trove.TIntProcedure;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.util.CharsetUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.io.JsonReaderEx;
import org.jetbrains.io.JsonUtil;
import org.jetbrains.io.webSocket.Client;
import org.jetbrains.io.webSocket.ExceptionHandler;
import org.jetbrains.io.webSocket.MessageServer;
import org.jetbrains.io.webSocket.WebSocketServer;

import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public class JsonRpcServer implements MessageServer {
  protected static final Logger LOG = Logger.getInstance(JsonRpcServer.class);

  private static final TypeAdapterFactory INT_LIST_TYPE_ADAPTER_FACTORY = new TypeAdapterFactory() {
    private IntArrayListTypeAdapter<TIntArrayList> typeAdapter;

    @Nullable
    @Override
    public <T> TypeAdapter<T> create(Gson gson, TypeToken<T> type) {
      if (type.getType() != TIntArrayList.class) {
        return null;
      }
      if (typeAdapter == null) {
        typeAdapter = new IntArrayListTypeAdapter<TIntArrayList>();
      }
      //noinspection unchecked
      return (TypeAdapter<T>)typeAdapter;
    }
  };

  private final AtomicInteger messageIdCounter = new AtomicInteger();
  private final WebSocketServer webSocketServer;
  private final ExceptionHandler exceptionHandler;
  private final Gson gson;

  private final Map<String, NotNullLazyValue> domains = new THashMap<String, NotNullLazyValue>();

  public JsonRpcServer(@NotNull WebSocketServer webSocketServer, @NotNull ExceptionHandler exceptionHandler) {
    this.webSocketServer = webSocketServer;
    this.exceptionHandler = exceptionHandler;
    gson = new GsonBuilder().registerTypeAdapter(CharSequenceBackedByArray.class, new JsonSerializer<CharSequenceBackedByArray>() {
      @Override
      public JsonElement serialize(CharSequenceBackedByArray src, Type typeOfSrc, JsonSerializationContext context) {
        return new JsonPrimitive(src.toString());
      }
    }).registerTypeAdapterFactory(INT_LIST_TYPE_ADAPTER_FACTORY).disableHtmlEscaping().create();
  }

  public void registerDomain(@NotNull String name, @NotNull NotNullLazyValue commands) {
    registerDomain(name, commands, false);
  }

  public void registerDomain(@NotNull String name, @NotNull NotNullLazyValue commands, boolean overridable) {
    if (domains.containsKey(name)) {
      if (overridable) {
        return;
      }
      else {
        throw new IllegalArgumentException(name + " is already registered");
      }
    }

    domains.put(name, commands);
  }

  @Override
  public void message(@NotNull Client client, String message) throws IOException {
    if (LOG.isDebugEnabled()) {
      LOG.debug("IN " + message);
    }

    JsonReaderEx reader = new JsonReaderEx(message);
    reader.beginArray();
    int messageId = reader.peek() == JsonToken.NUMBER ? reader.nextInt() : -1;
    String domainName = reader.nextString();
    if (domainName.length() == 1) {
      AsyncResult asyncResult = webSocketServer.removeAsyncResult(client, messageId);
      if (domainName.charAt(0) == 'r') {
        if (asyncResult == null) {
          LOG.error("Response with id " + messageId + " was already processed");
          return;
        }
        //noinspection unchecked
        asyncResult.setDone(JsonUtil.nextAny(reader));
      }
      else {
        asyncResult.setRejected();
      }
      return;
    }

    NotNullLazyValue domainHolder = domains.get(domainName);
    if (domainHolder == null) {
      LOG.error("Cannot find domain " + domainName);
      return;
    }

    Object domain = domainHolder.getValue();
    String command = reader.nextString();
    if (domain instanceof JsonServiceInvocator) {
      ((JsonServiceInvocator)domain).invoke(command, client, reader, message, messageId);
      return;
    }

    Object[] parameters;
    if (reader.hasNext()) {
      List<Object> list = new SmartList<Object>();
      JsonUtil.readListBody(reader, list);
      parameters = ArrayUtil.toObjectArray(list);
    }
    else {
      parameters = ArrayUtilRt.EMPTY_OBJECT_ARRAY;
    }

    reader.endArray();
    LOG.assertTrue(reader.peek() == JsonToken.END_DOCUMENT);

    try {
      boolean isStatic = domain instanceof Class;
      Method[] methods;
      if (isStatic) {
        methods = ((Class)domain).getDeclaredMethods();
      }
      else {
        methods = domain.getClass().getMethods();
      }
      for (Method method : methods) {
        if (method.getName().equals(command)) {
          method.setAccessible(true);
          Object result = method.invoke(isStatic ? null : domain, parameters);
          if (messageId != -1) {
            ByteBuf response = encodeMessage(messageId, null, null, new Object[]{result});
            if (response != null) {
              webSocketServer.sendResponse(client, response);
            }
          }
          return;
        }
      }

      throw new NoSuchMethodException(command);
    }
    catch (Throwable e) {
      throw new IOException(e);
    }
  }

  public void sendResponse(int messageId, @NotNull Client client, @Nullable CharSequence rawMessage) {
    ByteBuf response = encodeMessage(messageId, null, null, rawMessage, ArrayUtil.EMPTY_OBJECT_ARRAY);
    assert response != null;
    webSocketServer.sendResponse(client, response);
  }

  public void sendErrorResponse(int messageId, @NotNull Client client, @Nullable CharSequence rawMessage) {
    ByteBuf response = encodeMessage(messageId, "e", null, rawMessage, ArrayUtil.EMPTY_OBJECT_ARRAY);
    assert response != null;
    webSocketServer.sendResponse(client, response);
  }

  public void send(String domain, String name) {
    send(domain, name, null);
  }

  public <T> void send(String domain, String command, @Nullable final List<AsyncResult<Pair<Client, T>>> results, Object... params) {
    if (webSocketServer.hasClients()) {
      send(results == null ? -1 : messageIdCounter.getAndIncrement(), domain, command, results, params);
    }
  }

  @Nullable
  private ByteBuf encodeMessage(int messageId, @Nullable String domain, @Nullable String command, Object[] params) {
    return encodeMessage(messageId, domain, command, null, params);
  }

  @Nullable
  private ByteBuf encodeMessage(int messageId,
                                @Nullable String domain,
                                @Nullable String command,
                                @Nullable CharSequence rawMessage,
                                @Nullable Object[] params) {
    StringBuilder sb = new StringBuilder();
    try {
      encodeCall(sb, messageId, domain, command, params, rawMessage);
      if (LOG.isDebugEnabled()) {
        LOG.debug("OUT " + sb.toString());
      }
      return Unpooled.copiedBuffer(sb, CharsetUtil.UTF_8);
    }
    catch (IOException e) {
      exceptionHandler.exceptionCaught(e);
      return null;
    }
  }

  public boolean sendWithRawPart(Client client, String domain, String command, @Nullable CharSequence rawMessage, Object... params) {
    ByteBuf message = encodeMessage(-1, domain, command, rawMessage, params);
    if (message != null) {
      webSocketServer.send(client, -1, message);
    }
    return message != null;
  }

  public void send(Client client, String domain, String command, Object... params) {
    sendWithRawPart(client, domain, command, null, params);
  }

  public <T> AsyncResult<T> call(Client client, String domain, String command, Object... params) {
    int messageId = messageIdCounter.getAndIncrement();
    ByteBuf message = encodeMessage(messageId, domain, command, params);
    if (message == null) {
      return new AsyncResult.Rejected<T>();
    }

    AsyncResult<T> result = webSocketServer.send(client, messageId, message);
    return result == null ? new AsyncResult.Rejected<T>() : result;
  }

  private <T> void send(int messageId, @Nullable String domain, @Nullable String command, @Nullable final List<AsyncResult<Pair<Client, T>>> results, Object[] params) {
    ByteBuf message = encodeMessage(messageId, domain, command, params);
    if (message != null) {
      doSend(messageId, results, message);
    }
  }

  protected  <T> void doSend(int messageId, List<AsyncResult<Pair<Client, T>>> results, ByteBuf message) {
    webSocketServer.send(messageId, message, results);
  }

  private void encodeCall(StringBuilder sb, int id, @Nullable String domain, @Nullable String command, @Nullable Object[] params, @Nullable CharSequence rawData)
    throws IOException {
    sb.append('[');
    boolean hasPrev = false;
    if (id != -1) {
      sb.append(id);
      hasPrev = true;
    }

    if (domain != null) {
      if (hasPrev) {
        sb.append(',');
      }
      sb.append('"').append(domain).append("\",\"");

      if (command == null) {
        if (rawData != null) {
          sb.append(rawData);
        }
        sb.append('"');
        return;
      }
      else {
        sb.append(command).append('"');
      }
    }

    encodeParameters(sb, params == null ? ArrayUtil.EMPTY_OBJECT_ARRAY : params, rawData);
    sb.append(']');
  }

  private void encodeParameters(StringBuilder sb, Object[] params, @Nullable CharSequence rawData) throws IOException {
    if (params.length == 0 && rawData == null) {
      return;
    }

    JsonWriter writer = null;
    sb.append(',').append('[');
    boolean hasPrev = false;
    for (Object param : params) {
      if (hasPrev) {
        sb.append(',');
      }
      else {
        hasPrev = true;
      }

      // gson - SOE if param has type class com.intellij.openapi.editor.impl.DocumentImpl$MyCharArray, so, use hack
      if (param instanceof CharSequence) {
        JsonUtil.escape(((CharSequence)param), sb);
      }
      else if (param == null) {
        sb.append("null");
      }
      else if (param instanceof Number || param instanceof Boolean) {
        sb.append(param.toString());
      }
      else if (param instanceof Consumer) {
        //noinspection unchecked
        ((Consumer<StringBuilder>)param).consume(sb);
      }
      else {
        if (writer == null) {
          writer = new JsonWriter(Streams.writerForAppendable(sb));
        }
        //noinspection unchecked
        ((TypeAdapter<Object>)gson.getAdapter(param.getClass())).write(writer, param);
      }
    }

    if (rawData != null) {
      if (hasPrev) {
        sb.append(',');
      }
      sb.append(rawData);
    }
    sb.append(']');
  }

  private static class IntArrayListTypeAdapter<T> extends TypeAdapter<T> {
    @Override
    public void write(final JsonWriter out, T value) throws IOException {
      final Ref<IOException> error = new Ref<IOException>();
      out.beginArray();
      ((TIntArrayList)value).forEach(new TIntProcedure() {
        @Override
        public boolean execute(int value) {
          try {
            out.value(value);
          }
          catch (IOException e) {
            error.set(e);
          }
          return error.isNull();
        }
      });

      if (!error.isNull()) {
        throw error.get();
      }

      out.endArray();
    }

    @Override
    public T read(com.google.gson.stream.JsonReader in) throws IOException {
      throw new UnsupportedOperationException();
    }
  }
}
