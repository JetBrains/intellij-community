/*
 * Created by IntelliJ IDEA.
 * User: Alexey
 * Date: 11.04.2005
 * Time: 1:26:45
 */
package com.intellij.lang.properties.psi;

import com.intellij.psi.PsiFile;

public interface PropertiesFile extends PsiFile {
  Property[] getProperties();
  Property findPropertyByKey(String key);
}