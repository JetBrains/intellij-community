// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.javaFX.fxml.descriptors;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiReference;
import com.intellij.psi.xml.XmlElement;
import com.intellij.psi.xml.XmlTag;
import com.intellij.xml.XmlAttributeDescriptor;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.javaFX.JavaFXBundle;
import org.jetbrains.plugins.javaFX.fxml.FxmlConstants;
import org.jetbrains.plugins.javaFX.fxml.JavaFxPsiUtil;

import java.util.Arrays;
import java.util.stream.Stream;

/**
 * @author Pavel.Dolgov
 */
public class JavaFxRootTagDescriptor extends JavaFxClassTagDescriptorBase {
  private final XmlTag myXmlTag;

  public JavaFxRootTagDescriptor(XmlTag xmlTag) {
    super(FxmlConstants.FX_ROOT);
    myXmlTag = xmlTag;
  }

  @Override
  public PsiClass getPsiClass() {
    final String className = myXmlTag.getAttributeValue(FxmlConstants.TYPE);
    return className != null ? JavaFxPsiUtil.findPsiClass(className, myXmlTag) : null;
  }

  @Nullable
  @Override
  public XmlAttributeDescriptor getAttributeDescriptor(@NonNls String attributeName, @Nullable XmlTag context) {
    if (FxmlConstants.TYPE.equals(attributeName)) {
      return new RootTagTypeAttributeDescriptor();
    }
    return super.getAttributeDescriptor(attributeName, context);
  }

  @Override
  public XmlAttributeDescriptor[] getAttributesDescriptors(@Nullable XmlTag context) {
    return Stream.concat(Arrays.stream(super.getAttributesDescriptors(context)), Stream.of(new RootTagTypeAttributeDescriptor()))
      .toArray(XmlAttributeDescriptor.ARRAY_FACTORY::create);
  }

  @Override
  public boolean isReadOnlyAttribute(String attributeName) {
    return !FxmlConstants.TYPE.equals(attributeName) && super.isReadOnlyAttribute(attributeName);
  }

  @Override
  public void validate(@NotNull XmlTag context, @NotNull ValidationHost host) {
    super.validate(context, host);

    if (context.getParentTag() != null) {
      host.addMessage(context.getNavigationElement(),
                      JavaFXBundle.message("inspection.message.fx.root.valid.only.as.root.node.fxml.document"),
                      ValidationHost.ErrorType.ERROR);
    }
  }

  @Override
  public String toString() {
    final PsiClass psiClass = getPsiClass();
    return "<" + getName() + " -> " + (psiClass != null ? psiClass.getName() : myXmlTag.getAttributeValue(FxmlConstants.TYPE) + "?") + ">";
  }

  @Override
  public PsiElement getDeclaration() {
    final PsiClass psiClass = getPsiClass();
    return psiClass != null ? psiClass : myXmlTag;
  }

  public static class RootTagTypeAttributeDescriptor extends JavaFxPropertyAttributeDescriptor {
    public RootTagTypeAttributeDescriptor() {
      super(FxmlConstants.TYPE, null);
    }

    @Override
    public boolean isEnumerated() {
      return false;
    }

    @Override
    public boolean isRequired() {
      return true;
    }

    @Override
    protected PsiClass getEnum() {
      return null;
    }

    @Override
    protected boolean isConstant(PsiField field) {
      return false;
    }

    @Nullable
    @Override
    public String validateValue(XmlElement context, String value) {
      final PsiReference[] references = context.getReferences();
      if (references.length == 0 || references[references.length - 1].resolve() == null) {
        return JavaFXBundle.message("javafx.root.tag.descriptor.cannot.resolve.class", value);
      }
      return null;
    }

    @Override
    public String toString() {
      return FxmlConstants.FX_ROOT + "#" + getName();
    }
  }
}
