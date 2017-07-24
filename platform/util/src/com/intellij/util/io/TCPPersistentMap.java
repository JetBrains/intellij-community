/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.util.io;

import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.util.Consumer;
import com.intellij.util.Processor;

import org.jetbrains.annotations.NotNull;

import java.io.*;
import java.util.Arrays;
import java.util.Map;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

public class TCPPersistentMap<K, V> implements PersistentMap<K, V> {

  @NotNull private final File myFile;

  interface DurableMap {
    void getKey(int id, DataInputStream dis, DataOutputStream dos) throws IOException;

    void enumerateAllKeys(int id, DataOutputStream dos) throws IOException;
  }

  static class InlineDurableMap implements DurableMap {
    final PersistentMap<Integer, byte[]> storage;

    InlineDurableMap(PersistentMap<Integer, byte[]> storage) {
      this.storage = storage;
    }

    @Override
    public void getKey(int id, DataInputStream dis, DataOutputStream dos) throws IOException {
      int intKey = dis.readInt();
      dos.writeInt(id);
      byte[] bytes = storage.get(intKey);
      if (bytes == null) {
        dos.writeInt(0);
      }
      else {
        dos.write(bytes);
      }
    }

    @Override
    public void enumerateAllKeys(int id, DataOutputStream dos) throws IOException {
      dos.writeInt(id);
      dos.writeInt(0);
    }

    static InlineDurableMap create(String file, DataInputStream dis) throws IOException {
      BytesValueExternalizer valDesc = new BytesValueExternalizer();
      PersistentHashMap<Integer, byte[]> storage =
        new PersistentHashMap<Integer, byte[]>(new File(file + ".server"), new IntInlineKeyDescriptor(), valDesc);
      int count = dis.readInt();
      for (int i = 0; i < count; ++i) {
        int key = dis.readInt();
        byte[] val = valDesc.read(dis);
        storage.put(key, val);
      }
      return new InlineDurableMap(storage);
    }
  }

  static class BytesDurableMap implements DurableMap {
    private final BytesKeyDescriptor myDesc;
    final PersistentMap<byte[], byte[]> storage;

    BytesDurableMap(BytesKeyDescriptor keyDesc, PersistentMap<byte[], byte[]> storage) {
      myDesc = keyDesc;
      this.storage = storage;
    }

    static BytesDurableMap create(String file, DataInputStream dis) throws IOException {
      BytesKeyDescriptor keyDesc = new BytesKeyDescriptor();
      BytesValueExternalizer valDesc = new BytesValueExternalizer();
      PersistentHashMap<byte[], byte[]> storage = new PersistentHashMap<byte[], byte[]>(new File(file + ".server"), keyDesc, valDesc);
      while (true) {
        byte[] key = keyDesc.read(dis);
        if (key == null) break;
        byte[] val = valDesc.read(dis);
        storage.put(key, val);
      }
      return new BytesDurableMap(keyDesc, storage);
    }

    @Override
    public void getKey(int id, DataInputStream dis, DataOutputStream dos) throws IOException {
      byte[] key = myDesc.read(dis);
      byte[] val = storage.get(key);
      dos.writeInt(id);
      if (val == null) {
        dos.writeInt(0);
      }
      else {
        dos.write(val);
      }
    }

    @Override
    public void enumerateAllKeys(final int id, final DataOutputStream dos) throws IOException {
      storage.processKeys(new Processor<byte[]>() {
        @Override
        public boolean process(byte[] bytes) {
          try {
            dos.writeInt(id);
            dos.write(bytes);
          }
          catch (IOException e) {
            throw new RuntimeException(e);
          }
          return true;
        }
      });
      dos.writeInt(id);
      dos.writeInt(0);
    }
  }

  static class BytesKeyDescriptor implements KeyDescriptor<byte[]> {
    @Override
    public int getHashCode(byte[] value) {
      try {
        DataInputStream dis = new DataInputStream(new ByteArrayInputStream(value));
        dis.readInt(); // len
        return dis.readInt();
      }
      catch (IOException cause) {
        throw new RuntimeException(cause);
      }
    }

    @Override
    public boolean isEqual(byte[] val1, byte[] val2) {
      return Arrays.equals(val1, val2);
    }

    @Override
    public void save(@NotNull DataOutput out, byte[] value) throws IOException {
      out.write(value);
    }

    @Override
    public byte[] read(@NotNull DataInput in) throws IOException {
      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      DataOutputStream dos = new DataOutputStream(baos);
      int len = in.readInt();
      dos.writeInt(len);
      dos.writeInt(in.readInt()); //hashcode
      byte[] buf = new byte[len - 4];
      in.readFully(buf);
      dos.write(buf);
      return baos.toByteArray();
    }
  }

  static class BytesValueExternalizer implements DataExternalizer<byte[]> {
    @Override
    public void save(@NotNull DataOutput out, byte[] value) throws IOException {
      out.write(value);
    }

    @Override
    public byte[] read(@NotNull DataInput in) throws IOException {
      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      DataOutputStream dos = new DataOutputStream(baos);
      int len = in.readInt();
      dos.writeInt(len);
      byte[] b = new byte[len];
      in.readFully(b);
      dos.write(b);
      return baos.toByteArray();
    }
  }

  private static final Map<String, DurableMap> maps = new ConcurrentHashMap<String, DurableMap>();

  static void handleClient(DataInputStream in, DataOutputStream out) throws IOException {
    while (true) {
      final int id;
      try {
        id = in.readInt();
      }
      catch (EOFException e) {
        break;
      }
      String file = in.readUTF();
      byte op = in.readByte();
      switch (op) {
        case CREATE_MAP: {
          boolean isInline = in.readBoolean();
          DurableMap map;
          if (isInline) {
            map = InlineDurableMap.create(file, in);
          }
          else {
            map = BytesDurableMap.create(file, in);
          }
          maps.put(file, map);
        }
        break;
        case GET_KEY: {
          DurableMap map = maps.get(file);
          if (map == null) {
            System.out.println("map not found: " + file);
            out.writeInt(id);
            out.writeInt(0);
          } else {
            map.getKey(id, in, out);
          }
        }
        break;
        case PROCESS_KEYS: {
          DurableMap map = maps.get(file);
          if (map == null) {
            System.out.println("map not found: " + file);
            out.writeInt(id);
            out.writeInt(0);
          } else {
            map.enumerateAllKeys(id, out);
          }
        }
        break;
      }
    }

  }

  public static void main(String[] args) {
    try {
      ServerSocket socket = new ServerSocket(9999);
      while (true) {
        final Socket s = socket.accept();
        s.setTcpNoDelay(true);
        new Thread(new Runnable() {
          @Override
          public void run() {
            try {
              handleClient(new DataInputStream(s.getInputStream()), new DataOutputStream(s.getOutputStream()));
            }
            catch (IOException e) {
              e.printStackTrace();
            }
          }
        }, "client").start();
      }
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public static class Connection {
    final DataInputStream in;
    final DataOutputStream out;
    final ConcurrentLinkedQueue<Message> q = new ConcurrentLinkedQueue<Message>();
    final Object s = new Object();
    private volatile int writeCounter = 0;
    final Object writeLock = new Object();

    Connection(DataInputStream in, DataOutputStream out) {
      this.in = in;
      this.out = out;
    }

    static class Message {
      final byte[] arr;
      final int id;

      Message(byte[] arr, int id) {
        this.id = id;
        this.arr = arr;
      }
    }

    static Connection connect() {
      try {
        Socket localhost = new Socket("localhost", 9999);
        localhost.setTcpNoDelay(true);
        final DataInputStream in = new DataInputStream(localhost.getInputStream());
        DataOutputStream out = new DataOutputStream(localhost.getOutputStream());
        final Connection c = new Connection(in, out);
        new Thread(new Runnable() {
          @Override
          public void run() {
            try {
              while (true) {
                int id = in.readInt();
                int len = in.readInt();
                byte[] buf = new byte[len];
                in.readFully(buf);
                synchronized (c.s) {
                  c.q.offer(new Message(buf, id));
                  c.s.notifyAll();
                }
              }
            }
            catch (IOException e) {
              throw new RuntimeException(e);
            }
          }
        }, "reader").start();
        return c;
      }
      catch (IOException e) {
        throw new RuntimeException(e);
      }
    }

    int put(Consumer<DataOutputStream> writer) throws IOException {
      synchronized (writeLock) {
        out.writeInt(++writeCounter);
        writer.consume(out);
        return writeCounter;
      }
    }

    byte[] get(int i) {
      Message peek = null;
      while (peek == null || peek.id != i) {
        synchronized (s) {
          try {
            peek = q.peek();
            if (peek == null || peek.id != i) {
              s.wait();
            }
          }
          catch (InterruptedException ignore) {
          }
        }
      }
      synchronized (s) {
        Message read = q.poll();
        s.notifyAll();
        return read.arr;
      }
    }
  }

  static volatile Connection c;

  public static Connection connection() {
    if (c == null) {
      synchronized (Connection.class) {
        if (c == null) {
          c = Connection.connect();
        }
      }
    }
    return c;
  }

  static <K, V> DurableMapClient<K, V> createClient(File file, KeyDescriptor<K> keyDescriptor, DataExternalizer<V> valueDescriptor) {
    if (keyDescriptor instanceof InlineKeyDescriptor) {
      return new InlineMapClient<K, V>((InlineKeyDescriptor<K>)keyDescriptor, valueDescriptor, file);
    } else {
      return new BytesMapClient<K, V>(keyDescriptor, valueDescriptor, file);
    }
  }

  interface DurableMapClient<K, V> {
    V getKey(K key) throws IOException;

    boolean processKeys(Processor<K> processor) throws IOException;
  }

  static class InlineMapClient<K, V> implements DurableMapClient<K, V> {
    final InlineKeyDescriptor<K> myKeyDescriptor;
    final DataExternalizer<V> myValueDescriptor;
    final File myFile;

    InlineMapClient(InlineKeyDescriptor<K> descriptor, DataExternalizer<V> valueDescriptor, File file) {
      myKeyDescriptor = descriptor;
      myValueDescriptor = valueDescriptor;
      myFile = file;
    }


    @Override
    public V getKey(final K key) throws IOException {
      int id = connection().put(new Consumer<DataOutputStream>() {
        @Override
        public void consume(DataOutputStream stream) {
          try {
            stream.writeUTF(myFile.getPath());
            stream.writeByte(GET_KEY);
            stream.writeInt(myKeyDescriptor.toInt(key));
          }
          catch (IOException e) {
            throw new RuntimeException(e);
          }
        }
      });
      byte[] buf = connection().get(id);
      if (buf.length == 0) {
        return null;
      }
      else {
        return myValueDescriptor.read(new DataInputStream(new ByteArrayInputStream(buf)));
      }
    }

    @Override
    public boolean processKeys(Processor<K> processor) throws IOException {
      return false;
    }
  }

  static class BytesMapClient<K, V> implements DurableMapClient<K, V> {
    final KeyDescriptor<K> myKeyDescriptor;
    final DataExternalizer<V> myValueDescriptor;
    final File myFile;

    BytesMapClient(KeyDescriptor<K> descriptor, DataExternalizer<V> valueDescriptor, File file) {
      myKeyDescriptor = descriptor;
      myValueDescriptor = valueDescriptor;
      myFile = file;
    }

    @Override
    public V getKey(final K key) throws IOException {
      int id = connection().put(new Consumer<DataOutputStream>() {
        @Override
        public void consume(DataOutputStream stream) {
          try {
            stream.writeUTF(myFile.getPath());
            stream.writeByte(GET_KEY);

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            DataOutputStream dos = new DataOutputStream(baos);
            myKeyDescriptor.save(dos, key);
            stream.writeInt(dos.size() + 4);
            stream.writeInt(myKeyDescriptor.getHashCode(key));
            stream.write(baos.toByteArray());
          }
          catch (IOException e) {
            throw new RuntimeException(e);
          }
        }
      });

      byte[] buf = connection().get(id);
      if (buf.length == 0) {
        return null;
      }
      else {
        return myValueDescriptor.read(new DataInputStream(new ByteArrayInputStream(buf)));
      }
    }

    @Override
    public boolean processKeys(Processor<K> processor) throws IOException {
      int id = connection().put(new Consumer<DataOutputStream>() {
        @Override
        public void consume(DataOutputStream stream) {
          try {
            stream.writeUTF(myFile.getPath());
            stream.writeByte(PROCESS_KEYS);
          }
          catch (IOException e) {
            throw new RuntimeException(e);
          }
        }
      });
      boolean cont = true;
      ProcessCanceledException toPropagate = null;
      while (true) {
        byte[] read = connection().get(id);
        if (read.length == 0) break;
        if (cont) {
          DataInputStream dis = new DataInputStream(new ByteArrayInputStream(read));
          dis.readInt(); // hashcode
          K key = myKeyDescriptor.read(dis);
          try {
            cont = processor.process(key);
          } catch (ProcessCanceledException e) {
            toPropagate = e;
            cont = false;
          }
        }
      }
      if (toPropagate != null) {
        throw toPropagate;
      }
      return cont;
    }
  }

  final DurableMapClient<K, V> myClient;

  private volatile boolean closed = false;

  public TCPPersistentMap(@NotNull final File file,
                          @NotNull KeyDescriptor<K> keyDescriptor,
                          @NotNull DataExternalizer<V> valueExternalizer) {
    myFile = file;
    myClient = createClient(file, keyDescriptor, valueExternalizer);
  }

  static final int GET_KEY = 1;
  static final int PROCESS_KEYS = 2;
  static final int CREATE_MAP = 5;

  @Override
  public V get(final K key) throws IOException {
    long start = System.nanoTime();
    V value = myClient.getKey(key);
    long elapsed = System.nanoTime() - start;
    System.out.println("file: " + myFile.getPath() + " key: " + key + " elapsed:" + elapsed);
    return value;
  }

  @Override
  public void put(K key, V value) throws IOException {
  }

  @Override
  public void remove(K key) throws IOException {

  }

  @Override
  public boolean processKeys(Processor<K> processor) throws IOException {
    return myClient.processKeys(processor);
  }

  @Override
  public boolean isClosed() {
    return closed;
  }

  @Override
  public boolean isDirty() {
    return false;
  }

  @Override
  public void force() {

  }

  @Override
  public void close() throws IOException {
    closed = true;
  }

  @Override
  public void markDirty() throws IOException {

  }

  @Override
  public void clear() throws IOException {

  }
}
