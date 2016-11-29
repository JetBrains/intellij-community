/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package org.jetbrains.java.decompiler.struct.attr;

import org.jetbrains.java.decompiler.modules.decompiler.exps.AnnotationExprent;
import org.jetbrains.java.decompiler.struct.consts.ConstantPool;

import java.io.DataInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class StructAnnotationParameterAttribute extends StructGeneralAttribute {

  private List<List<AnnotationExprent>> paramAnnotations;

  @Override
  public void initContent(ConstantPool pool) throws IOException {
    DataInputStream data = stream();

    int len = data.readUnsignedByte();
    if (len > 0) {
      paramAnnotations = new ArrayList<>(len);
      for (int i = 0; i < len; i++) {
        List<AnnotationExprent> annotations = StructAnnotationAttribute.parseAnnotations(pool, data);
        paramAnnotations.add(annotations);
      }
    }
    else {
      paramAnnotations = Collections.emptyList();
    }
  }

  public List<List<AnnotationExprent>> getParamAnnotations() {
    return paramAnnotations;
  }
}
