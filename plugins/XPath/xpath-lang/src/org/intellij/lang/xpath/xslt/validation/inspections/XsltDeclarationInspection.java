package org.intellij.lang.xpath.xslt.validation.inspections;

import com.intellij.codeHighlighting.HighlightDisplayLevel;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.lang.LanguageNamesValidation;
import com.intellij.lang.refactoring.NamesValidator;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.XmlElementVisitor;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlTag;
import org.jetbrains.annotations.NotNull;

import org.intellij.lang.xpath.XPathFileType;
import org.intellij.lang.xpath.psi.impl.ResolveUtil;
import org.intellij.lang.xpath.xslt.XsltSupport;
import org.intellij.lang.xpath.xslt.psi.XsltElementFactory;
import org.intellij.lang.xpath.xslt.psi.XsltNamedElement;
import org.intellij.lang.xpath.xslt.psi.XsltTemplate;
import org.intellij.lang.xpath.xslt.validation.DeclarationChecker;

/*
* Created by IntelliJ IDEA.
* User: sweinreuter
* Date: 24.01.2008
*/
public class XsltDeclarationInspection extends XsltInspection {
    private final XsltElementFactory myXsltElementFactory = XsltElementFactory.getInstance();
    private final NamesValidator myNamesValidator = LanguageNamesValidation.INSTANCE.forLanguage(XPathFileType.XPATH.getLanguage());

    @NotNull
    public String getDisplayName() {
        return "Declaration Problems";
    }

    @NotNull
    public String getShortName() {
        return "XsltDeclarations";
    }

    @NotNull
    public HighlightDisplayLevel getDefaultLevel() {
        return HighlightDisplayLevel.ERROR;
    }

    @NotNull
    public PsiElementVisitor buildVisitor(@NotNull final ProblemsHolder holder, final boolean isOnTheFly) {
        return new XmlElementVisitor() {
            @Override
            public void visitXmlTag(final XmlTag tag) {
                final XmlAttribute nameAttr = tag.getAttribute("name", null);
                if (nameAttr != null && XsltSupport.isVariableOrParam(tag)) {
                    final XsltNamedElement instance = myXsltElementFactory.wrapElement(tag, XsltNamedElement.class);
                    checkDeclaration(instance, nameAttr.getValue(), true, holder, isOnTheFly);
                } else if (nameAttr != null && XsltSupport.isTemplate(tag)) {
                    final XsltTemplate tmpl = myXsltElementFactory.wrapElement(tag, XsltTemplate.class);
                    checkDeclaration(tmpl, nameAttr.getValue(), false, holder, isOnTheFly);
                }
            }

            private void checkDeclaration(final XsltNamedElement element, final String value, final boolean isVar, ProblemsHolder holder, boolean onTheFly) {
                final XmlTag tag = element.getTag();

                final PsiElement token = element.getNameIdentifier();
                if (value == null || value.length() == 0) {
                    if (token != null) {
                        holder.registerProblem(token, "Empty name not permitted");
                    } else {
                        final XmlAttribute attribute = element.getNameAttribute();
                        if (attribute != null) {
                            final XmlAttributeValue e = attribute.getValueElement();
                            if (e != null) {
                                holder.registerProblem(e, "Empty name not permitted");
                            }
                        }
                    }
                } else if (!isLegalName(value, holder.getManager().getProject())) {
                    assert token != null;
                    holder.registerProblem(token, "Illegal name");
                } else {
                    assert token != null;
                    ResolveUtil.treeWalkUp(new DeclarationChecker(isVar, tag, value, holder, isOnTheFly), tag);
                }
            }

            private boolean isLegalName(String value, Project project) {
                return myNamesValidator.isIdentifier(value, project);
            }
        };
    }
}