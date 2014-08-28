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

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class StructAnnotationParameterAttribute extends StructGeneralAttribute {

  private List<List<AnnotationExprent>> paramAnnotations;

  public void initContent(ConstantPool pool) {

    super.initContent(pool);

    paramAnnotations = new ArrayList<List<AnnotationExprent>>();
    DataInputStream data = new DataInputStream(new ByteArrayInputStream(info));

    try {
      int len = data.readUnsignedByte();
      for (int i = 0; i < len; i++) {
        List<AnnotationExprent> lst = new ArrayList<AnnotationExprent>();
        int annsize = data.readUnsignedShort();

        for (int j = 0; j < annsize; j++) {
          lst.add(StructAnnotationAttribute.parseAnnotation(data, pool));
        }
        paramAnnotations.add(lst);
      }
    }
    catch (IOException ex) {
      throw new RuntimeException(ex);
    }
  }

  public List<List<AnnotationExprent>> getParamAnnotations() {
    return paramAnnotations;
  }
}
