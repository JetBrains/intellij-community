package org.jetbrains.plugins.groovy.lang.psi.api.types;

import org.jetbrains.annotations.NotNull;

/**
 * @author: Dmitry.Krasilschikov
 * @date: 26.04.2007
 */
public interface GrClassTypeElement extends GrTypeElement {
  @NotNull
  GrCodeReferenceElement getReferenceElement();
}
