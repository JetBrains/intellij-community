/*
 * Copyright 2014 Higher Frequency Trading http://www.higherfrequencytrading.com
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
package org.jetbrains.xxh3;

import java.nio.Buffer;
import java.nio.ByteBuffer;

import static java.nio.ByteOrder.*;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

final class HashFunctionTest {
  public static void test(byte[] data, long eh) {
    int len = data.length;
    ByteBuffer bb = ByteBuffer.wrap(data).order(nativeOrder());
    testArrays(data, eh, len);
    testByteBuffers(eh, len, bb);
  }

  private static void testArrays(byte[] data, long eh, int len) {
    assertThat(Xxh3.hash(data)).isEqualTo(eh);

    byte[] data2 = new byte[len + 2];
    System.arraycopy(data, 0, data2, 1, len);
    assertThat(Xxh3.hash(data2, 1, len)).isEqualTo(eh);
  }

  private static void testByteBuffers(long eh, int len, ByteBuffer bb) {
    bb.order(LITTLE_ENDIAN);
    assertThat(Xxh3.hash(bb)).isEqualTo(eh);
    ByteBuffer bb2 = ByteBuffer.allocate(len + 2).order(LITTLE_ENDIAN);
    ((Buffer)bb2).position(1);
    bb2.put(bb);
    assertThat(Xxh3.hash(bb2, 1, len)).isEqualTo(eh);

    ((Buffer)bb.order(BIG_ENDIAN)).clear();

    assertThat(Xxh3.hash(bb)).isEqualTo(eh);
    bb2.order(BIG_ENDIAN);
    assertThat(Xxh3.hash(bb2, 1, len)).isEqualTo(eh);

    ((Buffer)bb.order(nativeOrder())).clear();
  }
}
