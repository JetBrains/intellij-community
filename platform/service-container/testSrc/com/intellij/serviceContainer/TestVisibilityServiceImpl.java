// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.serviceContainer;

class TestVisibilityServiceImpl extends TestVisibilityService {
  private String privateMethod() { return "private_impl"; }

  @Override
  public String publicMethod() { return "public_impl"; }

  @Override
  protected String protectedMethod() { return "protected_impl"; }

  @Override
  String packagePrivateMethod() { return "package_private_impl"; }
}
