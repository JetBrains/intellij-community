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
package org.jetbrains.java.decompiler.struct.consts;

public interface VariableTypeEnum {

  public final static int BOOLEAN = 1;
  public final static int BYTE = 2;
  public final static int CHAR = 3;
  public final static int SHORT = 4;
  public final static int INT = 5;
  public final static int FLOAT = 6;
  public final static int LONG = 7;
  public final static int DOUBLE = 8;
  public final static int RETURN_ADDRESS = 9;
  public final static int REFERENCE = 10;
  public final static int INSTANCE_UNINITIALIZED = 11;
  public final static int VALUE_UNKNOWN = 12;
  public final static int VOID = 13;

  public final static Integer BOOLEAN_OBJ = new Integer(BOOLEAN);
  public final static Integer BYTE_OBJ = new Integer(BYTE);
  public final static Integer CHAR_OBJ = new Integer(CHAR);
  public final static Integer SHORT_OBJ = new Integer(SHORT);
  public final static Integer INT_OBJ = new Integer(INT);
  public final static Integer FLOAT_OBJ = new Integer(FLOAT);
  public final static Integer LONG_OBJ = new Integer(LONG);
  public final static Integer DOUBLE_OBJ = new Integer(DOUBLE);
  public final static Integer RETURN_ADDRESS_OBJ = new Integer(RETURN_ADDRESS);
  public final static Integer REFERENCE_OBJ = new Integer(REFERENCE);
  public final static Integer INSTANCE_UNINITIALIZED_OBJ = new Integer(INSTANCE_UNINITIALIZED);
  public final static Integer VALUE_UNKNOWN_OBJ = new Integer(VALUE_UNKNOWN);
  public final static Integer VOID_OBJ = new Integer(VOID);
}
