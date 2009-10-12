/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

import java.io.DataOutput;
import java.io.IOException;
import java.util.Map;

/**
 * @author max
 */
public class WriteableMapAdapter<K,V> implements WriteableMap<V> {
  private final Map<K,V> myMap;
  private final ByteBufferMap.KeyProvider myKeyProvider;
  private final K[] myKeys;

  public WriteableMapAdapter(Map<K,V> map, ByteBufferMap.KeyProvider provider) {
    myMap = map;
    myKeyProvider = provider;
    myKeys = (K[]) myMap.keySet().toArray();
  }

  public int[] getHashCodesArray() {
    int[] keyHashCodes = new int[ myKeys.length ];
    for( int i = 0; i < myKeys.length; i++ )
      keyHashCodes[i] = myKeyProvider.hashCode(myKeys[i]);
    return keyHashCodes;
  }

  public V getValue( int n ) {
    return myMap.get( myKeys[n] );
  }

  public int getKeyLength( int n ) {
    return myKeyProvider.length( myKeys[n] );
  }

  public void writeKey( DataOutput out, int n ) throws IOException {
    myKeyProvider.write( out, myKeys[n] );
  }
}
