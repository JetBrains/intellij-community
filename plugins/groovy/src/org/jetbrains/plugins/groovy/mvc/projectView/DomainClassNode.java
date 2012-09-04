package org.jetbrains.plugins.groovy.mvc.projectView;

import com.intellij.ide.projectView.PresentationData;
import com.intellij.ide.projectView.ViewSettings;
import com.intellij.openapi.module.Module;
import com.intellij.psi.PsiElement;
import icons.JetgroovyIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;

/**
 * @author Dmitry Krasilschikov
 */
public class DomainClassNode extends ClassNode {
  public DomainClassNode(@NotNull final Module module,
                         @NotNull final GrTypeDefinition typeDefinition,
                         @Nullable final ViewSettings viewSettings) {
    super(module, typeDefinition, NodeId.DOMAIN_CLASS_IN_DOMAINS_SUBTREE, viewSettings);
  }

  @Override
  protected String getTestPresentationImpl(@NotNull final NodeId nodeId, @NotNull final PsiElement psiElement) {
    return "Domain class: " + ((GrTypeDefinition)psiElement).getName();
  }

  @Override
  protected void updateImpl(final PresentationData data) {
    super.updateImpl(data);
    data.setIcon(JetgroovyIcons.Mvc.Domain_class);
  }

  @Override
  public boolean validate() {
    if (!super.validate()) {
      return false;
    }
    return getValue() != null;
  }

}
