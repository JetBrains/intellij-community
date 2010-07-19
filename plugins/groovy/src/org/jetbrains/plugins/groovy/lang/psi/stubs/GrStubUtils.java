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

package org.jetbrains.plugins.groovy.lang.psi.stubs;

import com.intellij.psi.stubs.StubInputStream;
import com.intellij.psi.stubs.StubOutputStream;
import com.intellij.util.containers.ContainerUtil;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * User: Dmitry.Krasilschikov
 * Date: 02.06.2009
 */
public class GrStubUtils {
  public static List<Set<String>> deserializeCollectionsArray(StubInputStream dataStream) throws IOException {
    //named parameters
    final byte namedParametersSetNumber = dataStream.readByte();
    final List<Set<String>> collArray = new ArrayList<Set<String>>();

    for (int i = 0; i < namedParametersSetNumber; i++) {
      final byte curNamedParameterSetSize = dataStream.readByte();
      final String[] namedParameterSetArray = new String[curNamedParameterSetSize];

      for (int j = 0; j < curNamedParameterSetSize; j++) {
        namedParameterSetArray[j] = dataStream.readUTF();
      }
      Set<String> curSet = new HashSet<String>();
      ContainerUtil.addAll(curSet, namedParameterSetArray);
      collArray.add(curSet);
    }
    return collArray;
  }

  public static void serializeCollectionsArray(StubOutputStream dataStream, Set<String>[] collArray) throws IOException {
    dataStream.writeByte(collArray.length);
    for (Set<String> namedParameterSet : collArray) {
      dataStream.writeByte(namedParameterSet.size());
      for (String namepParameter : namedParameterSet) {
        dataStream.writeUTF(namepParameter);
      }
    }
  }  
}
