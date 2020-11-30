// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit.inspections;

import com.intellij.lang.ExternalAnnotatorsFilter;
import com.intellij.lang.annotation.ExternalAnnotator;
import com.intellij.lang.xml.XMLExternalAnnotator;
import com.intellij.openapi.application.ReadAction;
import com.intellij.psi.PsiFile;
import org.jetbrains.idea.devkit.util.DescriptorUtil;

/**
 * Filter out irrelevant errors from {@code com.intellij.xml.impl.ExternalDocumentValidator}.
 */
public final class PluginXmlExternalAnnotatorsFilter implements ExternalAnnotatorsFilter {

  @Override
  public boolean isProhibited(ExternalAnnotator annotator, PsiFile file) {
    if (!(annotator instanceof XMLExternalAnnotator)) {
      return false;
    }

    return ReadAction.compute(() -> {
      return DescriptorUtil.isPluginXml(file);
    });
  }
}
