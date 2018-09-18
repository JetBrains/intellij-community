// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.cvsSupport2.config;

import com.intellij.cvsSupport2.ui.experts.importToCvs.FileExtension;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.util.DefaultJDOMExternalizer;
import com.intellij.openapi.util.DifferenceFilter;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.util.text.StringUtil;
import org.jdom.Element;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class ImportConfiguration extends AbstractConfiguration {
  public String VENDOR;
  public String RELEASE_TAG;
  public String LOG_MESSAGE;
  public boolean CHECKOUT_AFTER_IMPORT = true;
  public String KEYWORD_SUBSTITUTION_WRAPPERS = "";
  public boolean MAKE_NEW_FILES_READ_ONLY = false;

  public static ImportConfiguration getInstance(){
    return ServiceManager.getService(ImportConfiguration.class);
  }

  public ImportConfiguration() {
    super("ImportConfiguration");
  }

  public Collection<FileExtension> getExtensions() {
    if (StringUtil.isEmpty(KEYWORD_SUBSTITUTION_WRAPPERS)) {
      return Collections.emptyList();
    }

    final ArrayList<FileExtension> result = new ArrayList<>();
    final String[] wrappers = KEYWORD_SUBSTITUTION_WRAPPERS.split(";");
    for (String wrapper : wrappers) {
      final String[] extAndSubstitution = wrapper.split(" ");
      if (extAndSubstitution.length != 2) continue;
      result.add(new FileExtension(extAndSubstitution[0], extAndSubstitution[1]));
    }
    return result;
  }

  public void setExtensions(List<FileExtension> items) {
    final StringBuilder buffer = new StringBuilder();
    for (FileExtension extension : items) {
      buffer.append(extension.getExtension());
      buffer.append(" ");
      buffer.append(extension.getKeywordSubstitution().getSubstitution().toString());
      buffer.append(";");
    }
    KEYWORD_SUBSTITUTION_WRAPPERS = buffer.toString();
  }

  @Override
  public void writeExternal(Element element) throws WriteExternalException {
    DefaultJDOMExternalizer.writeExternal(this, element, new DifferenceFilter<>(this, new ImportConfiguration()));
  }
}
