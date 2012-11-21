package org.jetbrains.android.refactoring;


import com.android.SdkConstants;
import com.android.ide.common.resources.configuration.FolderConfiguration;
import com.android.resources.ResourceType;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.command.UndoConfirmationPolicy;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.containers.HashSet;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.DomManager;
import org.jetbrains.android.actions.CreateResourceFileAction;
import org.jetbrains.android.dom.layout.Include;
import org.jetbrains.android.dom.layout.LayoutViewElement;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.util.AndroidCommonUtils;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * @author Eugene.Kudelevsky
 */
public class AndroidExtractAsIncludeAction extends AndroidBaseLayoutRefactoringAction {
  @NonNls public static final String ACTION_ID = "AndroidExtractAsIncludeAction";

  private final MyTestConfig myTestConfig;

  @SuppressWarnings("UnusedDeclaration")
  public AndroidExtractAsIncludeAction() {
    myTestConfig = null;
  }

  @TestOnly
  public AndroidExtractAsIncludeAction(@Nullable MyTestConfig testConfig) {
    myTestConfig = testConfig;

  }

  @Override
  protected void doRefactorForTags(@NotNull Project project, @NotNull XmlTag[] tags) {
    if (tags.length == 0) {
      return;
    }
    final PsiFile file = tags[0].getContainingFile();
    if (file == null) {
      return;
    }
    XmlTag startTag = null;
    XmlTag endTag = null;
    int startOffset = Integer.MAX_VALUE;
    int endOffset = -1;

    for (XmlTag tag : tags) {
      final TextRange range = tag.getTextRange();

      final int start = range.getStartOffset();
      if (start < startOffset) {
        startOffset = start;
        startTag = tag;
      }

      final int end = range.getEndOffset();
      if (end > endOffset) {
        endOffset = end;
        endTag = tag;
      }
    }
    assert startTag != null && endTag != null;
    doRefactorForPsiRange(project, file, startTag, endTag);
  }

  @Override
  protected boolean isEnabledForTags(@NotNull XmlTag[] tags) {
    if (tags.length == 0) {
      return false;
    }
    final DomManager domManager = DomManager.getDomManager(tags[0].getProject());
    boolean containsViewElement = false;

    for (XmlTag tag : tags) {
      final DomElement domElement = domManager.getDomElement(tag);

      if (!isSuitableDomElement(domElement)) {
        return false;
      }
      if (domElement instanceof LayoutViewElement) {
        containsViewElement = true;
      }
    }
    if (!containsViewElement) {
      return false;
    }
    final PsiElement parent = tags[0].getParent();

    if (!(parent instanceof XmlTag) || parent.getContainingFile() == null) {
      return false;
    }

    for (int i = 1; i < tags.length; i++) {
      if (tags[i].getParent() != parent) {
        return false;
      }
    }
    return true;
  }

  @Override
  protected void doRefactorForPsiRange(@NotNull final Project project, @NotNull final PsiFile file, @NotNull final PsiElement from,
                                       @NotNull final PsiElement to) {
    final PsiDirectory dir = file.getContainingDirectory();
    if (dir == null) {
      return;
    }
    final AndroidFacet facet = AndroidFacet.getInstance(from);
    assert facet != null;

    final XmlTag parentTag = PsiTreeUtil.getParentOfType(from, XmlTag.class);
    assert parentTag != null;

    final List<XmlTag> tagsInRange = collectAllTags(from, to);
    assert tagsInRange.size() > 0 : "there is no tag inside the range";
    final String fileName = myTestConfig != null ? myTestConfig.myLayoutFileName : null;
    final String dirName = dir.getName();
    final FolderConfiguration config = dirName.length() > 0
                                       ? FolderConfiguration.getConfig(dirName.split(SdkConstants.RES_QUALIFIER_SEP))
                                       : null;
    final String title = "Extract Android Layout";

    CommandProcessor.getInstance().executeCommand(project, new Runnable() {
      @Override
      public void run() {
        final XmlFile newFile =
          CreateResourceFileAction.createFileResource(facet, ResourceType.LAYOUT, fileName, "temp_root", config, true, title);

        if (newFile != null) {
          ApplicationManager.getApplication().runWriteAction(new Runnable() {
            @Override
            public void run() {
              doRefactor(facet, file, newFile, from, to, parentTag, tagsInRange.size() > 1);
            }
          });
        }
      }
    }, title, null, UndoConfirmationPolicy.REQUEST_CONFIRMATION);
  }

  private static void doRefactor(AndroidFacet facet,
                                 PsiFile file,
                                 XmlFile newFile,
                                 PsiElement from,
                                 PsiElement to,
                                 XmlTag parentTag,
                                 boolean wrapWithMerge) {
    final Project project = facet.getModule().getProject();
    final String textToExtract = file.getText().substring(from.getTextRange().getStartOffset(),
                                                          to.getTextRange().getEndOffset());
    final PsiDocumentManager documentManager = PsiDocumentManager.getInstance(project);
    final Document document = documentManager.getDocument(newFile);
    assert document != null;
    document.setText("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                     (wrapWithMerge ? "<merge>\n" + textToExtract + "\n</merge>" : textToExtract));
    documentManager.commitDocument(document);

    final Set<String> unknownPrefixes = new HashSet<String>();

    newFile.accept(new XmlRecursiveElementVisitor() {
      @Override
      public void visitXmlTag(XmlTag tag) {
        super.visitXmlTag(tag);
        final String prefix = tag.getNamespacePrefix();

        if (!unknownPrefixes.contains(prefix) && tag.getNamespace().length() == 0) {
          unknownPrefixes.add(prefix);
        }
      }

      @Override
      public void visitXmlAttribute(XmlAttribute attribute) {
        final String prefix = attribute.getNamespacePrefix();

        if (!unknownPrefixes.contains(prefix) && attribute.getNamespace().length() == 0) {
          unknownPrefixes.add(prefix);
        }
      }
    });

    final XmlTag rootTag = newFile.getRootTag();
    assert rootTag != null;
    final XmlElementFactory elementFactory = XmlElementFactory.getInstance(project);
    final XmlAttribute[] attributes = rootTag.getAttributes();
    final XmlAttribute firstAttribute = attributes.length > 0 ? attributes[0] : null;

    for (String prefix : unknownPrefixes) {
      final String namespace = parentTag.getNamespaceByPrefix(prefix);
      final String xmlNsAttrName = "xmlns:" + prefix;

      if (namespace.length() > 0 && rootTag.getAttribute(xmlNsAttrName) == null) {
        final XmlAttribute xmlnsAttr = elementFactory.createXmlAttribute(xmlNsAttrName, namespace);

        if (firstAttribute != null) {
          rootTag.addBefore(xmlnsAttr, firstAttribute);
        }
        else {
          rootTag.add(xmlnsAttr);
        }
      }
    }
    final String resourceName = AndroidCommonUtils.getResourceName(ResourceType.LAYOUT.getName(), newFile.getName());
    final XmlTag includeTag = elementFactory.createTagFromText("<include layout=\"@layout/" + resourceName + "\"/>");
    parentTag.addAfter(includeTag, to);
    parentTag.deleteChildRange(from, to);

    final CodeStyleManager codeStyleManager = CodeStyleManager.getInstance(project);
    codeStyleManager.reformat(newFile);
  }

  @NotNull
  private static String addXmlExtensionIfNecessary(@NotNull String inputString) {
    final String ext = FileUtil.getExtension(inputString);
    return "xml".equals(ext) ? inputString : inputString + ".xml";
  }

  @NotNull
  private static List<XmlTag> collectAllTags(PsiElement from, PsiElement to) {
    final List<XmlTag> result = new ArrayList<XmlTag>();
    PsiElement e = from;

    while (e != null) {
      if (e instanceof XmlTag) {
        result.add((XmlTag)e);
      }
      if (e == to) {
        break;
      }
      e = e.getNextSibling();
    }
    assert e != null : "invalid range";
    return result;
  }

  @Override
  protected boolean isEnabledForPsiRange(@NotNull PsiElement from, @Nullable PsiElement to) {
    final DomManager domManager = DomManager.getDomManager(from.getProject());
    PsiElement e = from;
    boolean containsViewElement = false;

    while (e != null) {
      if (e instanceof XmlTag) {
        final DomElement domElement = domManager.getDomElement((XmlTag)e);

        if (!isSuitableDomElement(domElement)) {
          return false;
        }
        if (domElement instanceof LayoutViewElement) {
          containsViewElement = true;
        }
      }
      if (e == to) {
        break;
      }
      e = e.getNextSibling();
    }
    return containsViewElement;
  }

  private static boolean isSuitableDomElement(DomElement element) {
    return element instanceof LayoutViewElement ||
           element instanceof Include;
  }

  static class MyTestConfig {
    private final String myLayoutFileName;

    MyTestConfig(@NotNull String layoutFileName) {
      myLayoutFileName = layoutFileName;
    }
  }
}
