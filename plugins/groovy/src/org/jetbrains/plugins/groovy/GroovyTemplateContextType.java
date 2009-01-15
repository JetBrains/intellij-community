/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package org.jetbrains.plugins.groovy;

import com.intellij.codeInsight.template.FileTypeBasedContextType;

/**
 * @author peter
 */
public class GroovyTemplateContextType extends FileTypeBasedContextType{

  protected GroovyTemplateContextType() {
    super("GROOVY", "Groovy", GroovyFileType.GROOVY_FILE_TYPE);
  }

}
