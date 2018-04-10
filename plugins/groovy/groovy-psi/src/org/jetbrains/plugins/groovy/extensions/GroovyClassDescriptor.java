/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package org.jetbrains.plugins.groovy.extensions;

import com.intellij.openapi.extensions.AbstractExtensionPointBean;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.util.xmlb.annotations.Attribute;
import com.intellij.util.xmlb.annotations.Property;
import com.intellij.util.xmlb.annotations.XCollection;

/**
 * @author Sergey Evdokimov
 */
public class GroovyClassDescriptor extends AbstractExtensionPointBean {

  public static final ExtensionPointName<GroovyClassDescriptor> EP_NAME = new ExtensionPointName<>("org.intellij.groovy.classDescriptor");

  @Attribute("class")
  //@Required
  public String className;

  @Property(surroundWithTag = false)
  @XCollection
  public GroovyMethodDescriptorTag[] methods;
}
