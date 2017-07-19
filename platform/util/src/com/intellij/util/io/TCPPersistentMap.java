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

import com.intellij.util.Consumer;
import com.intellij.util.Processor;
import org.jetbrains.annotations.NotNull;

import java.io.*;
import java.util.Arrays;
import java.util.Map;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.HashMap;

public class TCPPersistentMap<K, V> implements PersistentMap<K, V> {

  private static final Map<String, PersistentMap<byte[], byte[]>> maps = new HashMap<String, PersistentMap<byte[], byte[]>>();

  static PersistentMap<byte[], byte[]> getOrCreateMap(String file) {
    synchronized (maps) {
      PersistentMap<byte[], byte[]> pm = maps.get(file);
      if (pm != null) {
        return pm;
      } else {
        PersistentMap<byte[], byte[]> pm1 = null;
        try {
          pm1 = new PersistentHashMap<byte[], byte[]>(new File(file), new KeyDescriptor<byte[]>() {
            @Override
            public int getHashCode(byte[] value) {
              return Arrays.hashCode(value);
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

            }
          }, new DataExternalizer<byte[]>() {
            @Override
            public void save(@NotNull DataOutput out, byte[] value) throws IOException {

            }

            @Override
            public byte[] read(@NotNull DataInput in) throws IOException {
              return new byte[0];
            }
          });
        }
        catch (IOException e) {
          throw new RuntimeException(e);
        }
        maps.put(file, pm1);
        return pm1;
      }
    }

  }

  public static void main(String[] args) {
    try {
      ServerSocket socket = new ServerSocket(9999);
      Socket s = socket.accept();
      DataInputStream in = new DataInputStream(s.getInputStream());
      final DataOutputStream out = new DataOutputStream(s.getOutputStream());
      final Object writeLock = new Object();
      while (true) {
        final int id = in.readInt();
        String file = in.readUTF();
        PersistentMap<byte[], byte[]> pm = getOrCreateMap(file);
        byte op = in.readByte();
        switch(op) {
          case GET_KEY:
            int keyLen = in.readInt();
            byte[] buf = new byte[keyLen];
            in.readFully(buf);
            byte[] value = pm.get(buf);
            synchronized (writeLock) {
              out.writeInt(id);
              if (value != null) {
                out.writeInt(value.length);
                out.write(value);
              } else {
                out.writeInt(1);
                out.writeByte(NULL);
              }
            }
            break;
          case PROCESS_KEYS:
            pm.processKeys(new Processor<byte[]>() {
              @Override
              public boolean process(byte[] bytes) {
                try {
                  out.writeInt(id);
                  out.writeInt(bytes.length);
                  out.write(bytes);
                }
                catch (IOException e) {
                  throw new RuntimeException(e);
                }
                return true;
              }
            });
            out.writeInt(id);
            out.writeInt(1);
            out.writeByte(KEYS_END);
            break;
        }
      }
    } catch (IOException e) {

    }
  }

  private static class Connection {
    final DataInputStream in;
    final DataOutputStream out;
    final ConcurrentLinkedQueue<Message> q = new ConcurrentLinkedQueue<Message>();
    final Object s = new Object();
    volatile int writeCounter = 0;
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
        final DataInputStream in = new DataInputStream(localhost.getInputStream());
        DataOutputStream out = new DataOutputStream(localhost.getOutputStream());
        final Connection c = new Connection(in, out);
        new Thread(new Runnable() {
          @Override
          public void run() {
            try {
              int id = in.readInt();
              int len = in.readInt();
              byte[] buf = new byte[len];
              in.readFully(buf);
              c.q.offer(new Message(buf, id));
              c.s.notifyAll();
            }
            catch (IOException e) {
              throw new RuntimeException(e);
            }
          }
        }, "reader").start();
      }
      catch (IOException e) {
        throw new RuntimeException(e);
      }
      return c;
    }

    int put(Consumer<DataOutputStream> writer) throws IOException {
      synchronized (writeLock) {
        out.writeInt(++writeCounter);
        writer.consume(out);
        return writeCounter;
      }
    }

    byte[] get (int i) {
      while (q.peek().id != i) {
        try {
          s.wait();
        }
        catch (InterruptedException ignore) {
        }
      }
      Message read = q.poll();
      s.notifyAll();
      return read.arr;
    }
  }

  private static final Connection c = Connection.connect();

  private final File file;
  private final KeyDescriptor<K> keyDescriptor;
  private final DataExternalizer<V> valueExternalizer;
  private volatile boolean closed = false;

  public TCPPersistentMap(@NotNull final File file, @NotNull KeyDescriptor<K> keyDescriptor, @NotNull DataExternalizer<V> valueExternalizer) {
    this.file = file;
    this.keyDescriptor = keyDescriptor;
    this.valueExternalizer = valueExternalizer;
  }

  static final int GET_KEY = 1;
  static final int PROCESS_KEYS = 2;
  static final int KEYS_END = 3;
  static final int NULL = 4;

  static boolean isNull(byte[] buf) {
    return buf.length >= 1 && buf[0] == NULL;
  }

  static boolean isKeysEnd(byte [] buf) {
    return buf.length >= 1 && buf[0] == KEYS_END;
  }

  @Override
  public V get(final K key) throws IOException {
    int id = c.put(new Consumer<DataOutputStream>() {
      @Override
      public void consume(DataOutputStream stream) {
        try {
          stream.writeUTF(file.getPath());
          stream.writeByte(GET_KEY);
          ByteArrayOutputStream baos = new ByteArrayOutputStream();
          DataOutputStream dos = new DataOutputStream(baos);
          keyDescriptor.save(dos, key);
          stream.writeInt(dos.size());
          stream.write(baos.toByteArray());
        }
        catch (IOException e) {
          throw new RuntimeException(e);
        }
      }
    });

    byte[] buf = c.get(id);
    if (isNull(buf)) return null;
    return valueExternalizer.read(new DataInputStream(new ByteArrayInputStream(buf)));
  }

  @Override
  public void put(K key, V value) throws IOException {

  }

  @Override
  public void remove(K key) throws IOException {

  }

  @Override
  public boolean processKeys(Processor<K> processor) throws IOException {
    int id = c.put(new Consumer<DataOutputStream>() {
      @Override
      public void consume(DataOutputStream stream) {
        try {
          stream.writeUTF(file.getPath());
          stream.writeByte(PROCESS_KEYS);
        }
        catch (IOException e) {
          throw new RuntimeException(e);
        }
      }
    });
    boolean cont = true;
    while (true) {
      byte[] read = c.get(id);
      if (isKeysEnd(read)) break;
      if (cont) {
        K key = keyDescriptor.read(new DataInputStream(new ByteArrayInputStream(read)));
        cont = processor.process(key);
      }
    }
    return cont;
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
