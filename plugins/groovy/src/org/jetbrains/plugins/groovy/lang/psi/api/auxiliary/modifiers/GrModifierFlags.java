/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers;

/**
 * @author Maxim.Medvedev
 */
public interface GrModifierFlags {
  int PUBLIC_MASK = 0x0001;
  int PRIVATE_MASK = 0x0002;
  int PROTECTED_MASK = 0x0004;
  int STATIC_MASK = 0x0008;
  int FINAL_MASK = 0x0010;
  int SYNCHRONIZED_MASK = 0x0020;
  int VOLATILE_MASK = 0x0040;
  int TRANSIENT_MASK = 0x0080;
  int NATIVE_MASK = 0x0100;
  int INTERFACE_MASK = 0x0200;
  int ABSTRACT_MASK = 0x0400;
  int STRICTFP_MASK = 0x0800;
  int PACKAGE_LOCAL_MASK = 0x1000;
  int DEPRECATED_MASK = 0x2000;
  int ENUM_MASK = 0x4000;
  int ANNOTATION_TYPE_MASK = 0x8000;
  int ANNOTATION_DEPRECATED_MASK = 0x10000;
  int DEF_MASK = 0x20000;
}
