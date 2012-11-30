/*
 * Copyright 2000-2010 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.android.inspections;

import com.android.SdkConstants;
import com.intellij.codeInspection.*;
import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.XmlRecursiveElementVisitor;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlChildRole;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.containers.HashSet;
import com.intellij.util.xml.DomFileDescription;
import org.jetbrains.android.dom.AndroidAnyAttributeDescriptor;
import org.jetbrains.android.dom.AndroidResourceDomFileDescription;
import org.jetbrains.android.dom.AndroidXmlTagDescriptor;
import org.jetbrains.android.dom.manifest.ManifestDomFileDescription;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * @author Eugene.Kudelevsky
 */
public class AndroidUnknownAttributeInspection extends LocalInspectionTool {
  private static volatile Set<String> ourSupportedResourceTypes;

  @Nls
  @NotNull
  @Override
  public String getGroupDisplayName() {
    return AndroidBundle.message("android.inspections.group.name");
  }

  @Nls
  @NotNull
  @Override
  public String getDisplayName() {
    return AndroidBundle.message("android.inspections.unknown.attribute.name");
  }

  @NotNull
  @Override
  public String getShortName() {
    return "AndroidUnknownAttribute";
  }

  @Override
  public ProblemDescriptor[] checkFile(@NotNull PsiFile file, @NotNull InspectionManager manager, boolean isOnTheFly) {
    if (!(file instanceof XmlFile)) {
      return ProblemDescriptor.EMPTY_ARRAY;
    }
    AndroidFacet facet = AndroidFacet.getInstance(file);
    if (facet == null) {
      return ProblemDescriptor.EMPTY_ARRAY;
    }
    if (isMyFile(facet, file)) {
      MyVisitor visitor = new MyVisitor(manager, isOnTheFly);
      file.accept(visitor);
      return visitor.myResult.toArray(new ProblemDescriptor[visitor.myResult.size()]);
    }
    return ProblemDescriptor.EMPTY_ARRAY;
  }

  static boolean isMyFile(AndroidFacet facet, PsiFile file) {
    String resourceType = facet.getLocalResourceManager().getFileResourceType(file);
    if (resourceType != null) {
      if (ourSupportedResourceTypes == null) {
        ourSupportedResourceTypes = new HashSet<String>();
        for (DomFileDescription description : DomFileDescription.EP_NAME.getExtensions()) {
          if (description instanceof AndroidResourceDomFileDescription) {
            String[] resourceTypes = ((AndroidResourceDomFileDescription)description).getResourceTypes();
            Collections.addAll(ourSupportedResourceTypes, resourceTypes);
          }
        }
      }
      return ourSupportedResourceTypes.contains(resourceType);
    }
    return ManifestDomFileDescription.isManifestFile((XmlFile)file);
  }

  private static class MyVisitor extends XmlRecursiveElementVisitor {
    private final InspectionManager myInspectionManager;
    private final boolean myOnTheFly;
    final List<ProblemDescriptor> myResult = new ArrayList<ProblemDescriptor>();

    private MyVisitor(InspectionManager inspectionManager, boolean onTheFly) {
      myInspectionManager = inspectionManager;
      myOnTheFly = onTheFly;
    }

    @Override
    public void visitXmlAttribute(XmlAttribute attribute) {
      if (!"xmlns".equals(attribute.getNamespacePrefix())) {
        String namespace = attribute.getNamespace();

        if (SdkConstants.NS_RESOURCES.equals(namespace) || namespace.isEmpty()) {
          final XmlTag tag = attribute.getParent();

          if (tag != null &&
              tag.getDescriptor() instanceof AndroidXmlTagDescriptor &&
              attribute.getDescriptor() instanceof AndroidAnyAttributeDescriptor) {
            final ASTNode node = attribute.getNode();
            assert node != null;
            ASTNode nameNode = XmlChildRole.ATTRIBUTE_NAME_FINDER.findChild(node);
            final PsiElement nameElement = nameNode != null ? nameNode.getPsi() : null;
            if (nameElement != null) {
              myResult.add(myInspectionManager.createProblemDescriptor(nameElement, AndroidBundle
                .message("android.inspections.unknown.attribute.message", attribute.getName()), myOnTheFly, LocalQuickFix.EMPTY_ARRAY,
                                                                       ProblemHighlightType.GENERIC_ERROR_OR_WARNING));
            }
          }
        }
      }
    }
  }
}
