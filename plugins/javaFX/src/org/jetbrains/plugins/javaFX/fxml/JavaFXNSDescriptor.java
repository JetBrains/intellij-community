package org.jetbrains.plugins.javaFX.fxml;

import com.intellij.codeInsight.daemon.Validator;
import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.*;
import com.intellij.util.ArrayUtil;
import com.intellij.xml.XmlElementDescriptor;
import com.intellij.xml.XmlNSDescriptor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.javaFX.fxml.descriptors.JavaFxClassBackedElementDescriptor;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

/**
* User: anna
* Date: 1/9/13
*/
public class JavaFXNSDescriptor implements XmlNSDescriptor, Validator<XmlDocument> {
  private XmlFile myFile;

  @Nullable
  @Override
  public XmlElementDescriptor getElementDescriptor(@NotNull XmlTag tag) {
    final String name = tag.getName();

    if (JavaFxClassBackedElementDescriptor.isClassTag(name)) {
      return new JavaFxClassBackedElementDescriptor(name, tag);
    }
    else {
      final XmlTag parentTag = tag.getParentTag();
      if (parentTag != null) {
        final XmlElementDescriptor descriptor = parentTag.getDescriptor();
        if (descriptor != null) {
          return descriptor.getElementDescriptor(tag, parentTag);
        }
      }
    }
    return null;
  }

  public static List<String> parseImports(XmlFile file) {
    List<String> definedImports = new ArrayList<String>();
    XmlDocument document = file.getDocument();
    if (document != null) {
      XmlProlog prolog = document.getProlog();

      final Collection<XmlProcessingInstruction>
        instructions = new ArrayList<XmlProcessingInstruction>(PsiTreeUtil.findChildrenOfType(prolog, XmlProcessingInstruction.class));
      for (Iterator<XmlProcessingInstruction> iterator = instructions.iterator(); iterator.hasNext(); ) {
        final XmlProcessingInstruction instruction = iterator.next();
        final ASTNode node = instruction.getNode();
        ASTNode xmlNameNode = node.findChildByType(XmlTokenType.XML_NAME);
        ASTNode importNode = node.findChildByType(XmlTokenType.XML_TAG_CHARACTERS);
        if (xmlNameNode == null || !"import".equals(xmlNameNode.getText()) || importNode == null) {
          iterator.remove();
        } else {
          definedImports.add(importNode.getText());
        }
      }
    }
    return definedImports;
  }

  @NotNull
  @Override
  public XmlElementDescriptor[] getRootElementsDescriptors(@Nullable XmlDocument document) {
    //todo
    return new XmlElementDescriptor[0];
  }

  @Nullable
   public XmlFile getDescriptorFile() {
     return myFile;
   }
 
   public boolean isHierarhyEnabled() {
     return false;
   }
 
   public PsiElement getDeclaration() {
     return myFile;
   }

  @Override
  public String getName(PsiElement context) {
    return null;
  }

  @Override
  public String getName() {
    return null;
  }

  @Override
  public void init(PsiElement element) {
    XmlDocument document = (XmlDocument) element;
    myFile = ((XmlFile)document.getContainingFile());
  }

  @Override
  public Object[] getDependences() {
    return ArrayUtil.EMPTY_OBJECT_ARRAY;
  }

  @Override
  public void validate(@NotNull XmlDocument context, @NotNull ValidationHost host) {
    //todo check that node has correct type
  }
}
