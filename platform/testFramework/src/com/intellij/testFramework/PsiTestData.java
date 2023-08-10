// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.testFramework;

import com.intellij.openapi.util.DefaultJDOMExternalizer;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMExternalizable;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import org.jdom.Element;

import java.io.File;
import java.io.IOException;

/**
 * @deprecated use other to configure test data files
 */
@Deprecated
public class PsiTestData implements JDOMExternalizable {
  public String TEXT_FILE = "";
  private String myText;

  public String getTextFile() {
    return TEXT_FILE;
  }

  public String getText() {
    return myText;
  }

  public void loadText(String root) throws IOException{
    String fileName = root + "/" + TEXT_FILE;
    myText = FileUtil.loadFile(new File(fileName));
    myText = StringUtil.convertLineSeparators(myText);
  }

  @Override
  public void readExternal(Element element) throws InvalidDataException {
    DefaultJDOMExternalizer.readExternal(this, element);
  }

  @Override
  public void writeExternal(Element element) throws WriteExternalException {
    DefaultJDOMExternalizer.writeExternal(this, element);
  }
}
