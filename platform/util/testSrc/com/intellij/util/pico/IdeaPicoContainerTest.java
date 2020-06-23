// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.pico;

import org.junit.Before;
import org.junit.Test;

import static com.intellij.testFramework.assertions.Assertions.assertThat;

public class IdeaPicoContainerTest {
  private DefaultPicoContainer myContainer;

  @Before
  public void setUp() {
    myContainer = new DefaultPicoContainer();
  }

  @Test
  public void testUnregister() {
    MyComponentClass instance = new MyComponentClass();
    Class<?> key = MyComponentClass.class;
    myContainer.registerComponentInstance(key, instance);
    assertThat(myContainer.getComponentAdaptersOfType(MyComponentClass.class)).hasSize(1);
    myContainer.unregisterComponent(key);
    assertThat(myContainer.getComponentAdaptersOfType(MyComponentClass.class)).isEmpty();
  }
}

final class MyComponentClass {}