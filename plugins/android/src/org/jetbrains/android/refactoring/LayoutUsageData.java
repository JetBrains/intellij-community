package org.jetbrains.android.refactoring;

import com.android.SdkConstants;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.XmlElementFactory;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlChildRole;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.containers.HashMap;
import org.jetbrains.android.dom.converters.AndroidResourceReferenceBase;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * @author Eugene.Kudelevsky
 */
public class LayoutUsageData {
  private final Project myProject;
  private final XmlTag myIncludeTag;
  private final AndroidResourceReferenceBase myReference;

  public LayoutUsageData(@NotNull Project project,
                         @NotNull XmlTag includeTag,
                         @NotNull AndroidResourceReferenceBase reference) {
    myProject = project;
    myIncludeTag = includeTag;
    myReference = reference;
  }

  @NotNull
  public AndroidResourceReferenceBase getReference() {
    return myReference;
  }

  public void inline(@NotNull XmlTag layoutRootTag) throws AndroidRefactoringErrorException {
    final XmlTag parent = myIncludeTag.getParentTag();

    if ("merge".equals(layoutRootTag.getName()) && parent != null) {
      inlineMultiTags(myIncludeTag, parent, layoutRootTag, myProject);
    }
    else {
      inlineSingleTag(myIncludeTag, parent, layoutRootTag);
    }
  }

  private static void inlineSingleTag(XmlTag includeTag, XmlTag includeParentTag, XmlTag layoutRootTag) {
    final Map<String, String> attributesToAdd = new HashMap<String, String>();

    for (XmlAttribute attribute : includeTag.getAttributes()) {
      final String namespace = attribute.getNamespace();

      if (SdkConstants.NS_RESOURCES.equals(namespace)) {
        attributesToAdd.put(attribute.getLocalName(), attribute.getValue());
      }
    }
    final XmlTag newTag = (XmlTag)includeTag.replace(layoutRootTag.copy());
    final List<XmlAttribute> toDelete = new ArrayList<XmlAttribute>();

    for (XmlAttribute attribute : newTag.getAttributes()) {
      if (attribute.isNamespaceDeclaration()) {
        final String localName = attribute.getLocalName();
        final String prefix = localName.equals(attribute.getName()) ? "" : localName;
        final String namespace = attribute.getValue();

        if (includeParentTag != null && namespace.equals(includeParentTag.getNamespaceByPrefix(prefix))) {
          toDelete.add(attribute);
        }
      }
    }

    for (XmlAttribute attribute : toDelete) {
      attribute.delete();
    }

    for (Map.Entry<String, String> entry : attributesToAdd.entrySet()) {
      final String localName = entry.getKey();
      final String value = entry.getValue();
      newTag.setAttribute(localName, SdkConstants.NS_RESOURCES, value);
    }
    CodeStyleManager.getInstance(newTag.getManager()).reformat(newTag);
  }

  private static void inlineMultiTags(XmlTag includeTag, XmlTag includeTagParent, XmlTag mergeTag, Project project)
    throws AndroidRefactoringErrorException {
    final Map<String, String> namespacesFromParent = includeTagParent.getLocalNamespaceDeclarations();
    final Map<String, String> namespacesToAddToParent = new HashMap<String, String>();
    final Map<String, String> namespacesToAddToEachTag = new HashMap<String, String>();

    for (Map.Entry<String, String> entry : mergeTag.getLocalNamespaceDeclarations().entrySet()) {
      final String prefix = entry.getKey();
      final String namespace = entry.getValue();
      final String declaredNamespace = namespacesFromParent.get(prefix);

      if (declaredNamespace != null && !declaredNamespace.equals(namespace)) {
        namespacesToAddToEachTag.put(prefix, namespace);
      }
      else {
        namespacesToAddToParent.put(prefix, namespace);
      }
    }
    final XmlTag mergeTagCopy = (XmlTag)mergeTag.copy();
    final XmlElementFactory xmlElementFactory = XmlElementFactory.getInstance(project);

    for (XmlTag subtag : mergeTagCopy.getSubTags()) {
      final XmlAttribute[] attributes = subtag.getAttributes();
      final XmlAttribute firstAttribute = attributes.length > 0 ? attributes[0] : null;

      for (Map.Entry<String, String> entry : namespacesToAddToEachTag.entrySet()) {
        final String prefix = entry.getKey();
        final String namespace = entry.getValue();

        if (!subtag.getLocalNamespaceDeclarations().containsKey(prefix)) {
          final XmlAttribute xmlnsAttr = xmlElementFactory.createXmlAttribute("xmlns:" + prefix, namespace);

          if (firstAttribute != null) {
            subtag.addBefore(xmlnsAttr, firstAttribute);
          }
          else {
            subtag.add(xmlnsAttr);
          }
        }
      }
    }
    replaceByTagContent(project, includeTag, mergeTagCopy);
    addNamespaceAttributes(includeTagParent, namespacesToAddToParent, project);
  }

  private static void addNamespaceAttributes(XmlTag tag, Map<String, String> namespaces, Project project) {
    final XmlAttribute[] parentAttributes = tag.getAttributes();
    final XmlAttribute firstParentAttribute = parentAttributes.length > 0 ? parentAttributes[0] : null;
    final XmlElementFactory factory = XmlElementFactory.getInstance(project);

    for (Map.Entry<String, String> entry : namespaces.entrySet()) {
      final String prefix = entry.getKey();
      final String namespace = entry.getValue();

      if (!namespace.equals(tag.getNamespaceByPrefix(prefix))) {
        final XmlAttribute xmlnsAttr = factory.createXmlAttribute("xmlns:" + prefix, namespace);

        if (firstParentAttribute != null) {
          tag.addBefore(xmlnsAttr, firstParentAttribute);
        }
        else {
          tag.add(xmlnsAttr);
        }
      }
    }
  }

  private static void replaceByTagContent(Project project, XmlTag tagToReplace, XmlTag tagToInline)
    throws AndroidRefactoringErrorException {
    final ASTNode node = tagToInline.getNode();

    if (node == null) {
      throw new AndroidRefactoringErrorException();
    }
    final ASTNode startTagEnd = XmlChildRole.START_TAG_END_FINDER.findChild(node);
    final ASTNode closingTagStart = XmlChildRole.CLOSING_TAG_START_FINDER.findChild(node);

    if (startTagEnd == null || closingTagStart == null) {
      throw new AndroidRefactoringErrorException();
    }
    final int contentStart = startTagEnd.getTextRange().getEndOffset();
    final int contentEnd = closingTagStart.getTextRange().getStartOffset();

    if (contentStart < 0 || contentEnd < 0 || contentStart >= contentEnd) {
      throw new AndroidRefactoringErrorException();
    }
    final PsiFile file = tagToInline.getContainingFile();

    if(file == null) {
      throw new AndroidRefactoringErrorException();
    }
    final String textToInline = file.getText().
      substring(contentStart, contentEnd).trim();
    final PsiDocumentManager documentManager = PsiDocumentManager.getInstance(project);
    final Document document = documentManager.getDocument(tagToReplace.getContainingFile());

    if (document == null) {
      throw new AndroidRefactoringErrorException();
    }
    final TextRange range = tagToReplace.getTextRange();
    document.replaceString(range.getStartOffset(), range.getEndOffset(), textToInline);
    documentManager.commitDocument(document);
  }
}
