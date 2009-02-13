/*
 * Copyright (c) 2000-2007 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.lang.properties.psi;

import com.intellij.psi.PsiLiteralExpression;
import org.jetbrains.annotations.Nullable;

/**
 * @author nik
 */
public abstract class I18nizedTextGenerator {

  public abstract String getI18nizedText(String propertyKey, final @Nullable PropertiesFile propertiesFile,
                                         final PsiLiteralExpression context);

  public abstract String getI18nizedConcatenationText(String propertyKey, String parametersString,
                                                      final @Nullable PropertiesFile propertiesFile, final PsiLiteralExpression context);

}
