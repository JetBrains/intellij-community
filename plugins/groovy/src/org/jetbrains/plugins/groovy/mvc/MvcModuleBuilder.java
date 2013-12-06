/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.mvc;

import org.jetbrains.plugins.groovy.config.GroovyAwareModuleBuilder;

import javax.swing.*;

/**
 * @author peter
 */
public class MvcModuleBuilder extends GroovyAwareModuleBuilder {
  private final MvcFramework myFramework;

  protected MvcModuleBuilder(MvcFramework framework, Icon bigIcon) {
    super(framework.getFrameworkName(), framework.getDisplayName(),
          framework.getDisplayName() + " modules are used for creating <b>" + framework.getDisplayName() + "</b> applications.", bigIcon);
    myFramework = framework;
  }

  @Override
  protected MvcFramework getFramework() {
    return myFramework;
  }
}
