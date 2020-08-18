// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.mvc;

import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.config.GroovyAwareModuleBuilder;

/**
 * @author peter
 */
public class MvcModuleBuilder extends GroovyAwareModuleBuilder {
  private final MvcFramework myFramework;

  protected MvcModuleBuilder(MvcFramework framework) {
    super(framework.getFrameworkName(), framework.getDisplayName(),
          GroovyBundle.message("mvc.framework.0.module.builder.description", framework.getDisplayName()));
    myFramework = framework;
  }

  @Override
  protected MvcFramework getFramework() {
    return myFramework;
  }
}
