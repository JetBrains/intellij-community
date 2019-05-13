// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
  int DEFAULT_MASK = 0x40000;
}
