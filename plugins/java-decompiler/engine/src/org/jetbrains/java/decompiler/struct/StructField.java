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
package org.jetbrains.java.decompiler.struct;

import org.jetbrains.java.decompiler.struct.attr.StructGeneralAttribute;
import org.jetbrains.java.decompiler.struct.consts.ConstantPool;
import org.jetbrains.java.decompiler.util.VBStyleCollection;

import java.io.DataOutputStream;
import java.io.IOException;

/*
         field_info {
    	u2 access_flags;
    	u2 name_index;
    	u2 descriptor_index;
    	u2 attributes_count;
    	attribute_info attributes[attributes_count];
    }
*/

public class StructField {

  // *****************************************************************************
  // public fields
  // *****************************************************************************

  public int access_flags;
  public int name_index;
  public int descriptor_index;

  private String name;
  private String descriptor;

  // *****************************************************************************
  // private fields
  // *****************************************************************************

  private VBStyleCollection<StructGeneralAttribute, String> attributes;

  // *****************************************************************************
  // constructors
  // *****************************************************************************

  public StructField() {
  }

  // *****************************************************************************
  // public methods
  // *****************************************************************************

  public void writeToStream(DataOutputStream out) throws IOException {

    out.writeShort(access_flags);
    out.writeShort(name_index);
    out.writeShort(descriptor_index);

    out.writeShort(attributes.size());
    for (StructGeneralAttribute attr : attributes) {
      attr.writeToStream(out);
    }
  }

  public void initStrings(ConstantPool pool, int class_index) {
    String[] values = pool.getClassElement(ConstantPool.FIELD, class_index, name_index, descriptor_index);
    name = values[0];
    descriptor = values[1];
  }

  // *****************************************************************************
  // getter and setter methods
  // *****************************************************************************

  public VBStyleCollection<StructGeneralAttribute, String> getAttributes() {
    return attributes;
  }

  public void setAttributes(VBStyleCollection<StructGeneralAttribute, String> attributes) {
    this.attributes = attributes;
  }

  public String getDescriptor() {
    return descriptor;
  }

  public void setDescriptor(String descriptor) {
    this.descriptor = descriptor;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }
}
