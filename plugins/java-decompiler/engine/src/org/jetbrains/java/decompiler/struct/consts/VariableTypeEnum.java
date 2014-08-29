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

  int BOOLEAN = 1;
  int BYTE = 2;
  int CHAR = 3;
  int SHORT = 4;
  int INT = 5;
  int FLOAT = 6;
  int LONG = 7;
  int DOUBLE = 8;
  int RETURN_ADDRESS = 9;
  int REFERENCE = 10;
  int INSTANCE_UNINITIALIZED = 11;
  int VALUE_UNKNOWN = 12;
  int VOID = 13;

  Integer BOOLEAN_OBJ = new Integer(BOOLEAN);
  Integer BYTE_OBJ = new Integer(BYTE);
  Integer CHAR_OBJ = new Integer(CHAR);
  Integer SHORT_OBJ = new Integer(SHORT);
  Integer INT_OBJ = new Integer(INT);
  Integer FLOAT_OBJ = new Integer(FLOAT);
  Integer LONG_OBJ = new Integer(LONG);
  Integer DOUBLE_OBJ = new Integer(DOUBLE);
  Integer RETURN_ADDRESS_OBJ = new Integer(RETURN_ADDRESS);
  Integer REFERENCE_OBJ = new Integer(REFERENCE);
  Integer INSTANCE_UNINITIALIZED_OBJ = new Integer(INSTANCE_UNINITIALIZED);
  Integer VALUE_UNKNOWN_OBJ = new Integer(VALUE_UNKNOWN);
  Integer VOID_OBJ = new Integer(VOID);
}
