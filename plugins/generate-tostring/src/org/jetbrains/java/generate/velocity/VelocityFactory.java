/*
 * Copyright 2001-2007 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.java.generate.velocity;

import com.intellij.codeInsight.generation.VelocityIncludesClassLoader;
import com.intellij.openapi.util.ClassLoaderUtil;
import org.apache.commons.collections.ExtendedProperties;
import org.apache.velocity.app.VelocityEngine;
import org.apache.velocity.runtime.RuntimeConstants;
import org.apache.velocity.runtime.log.JdkLogChute;
import org.apache.velocity.runtime.log.SimpleLog4JLogSystem;

/**
 * Velocity factory.
 * <p/>
 * Creating instances of the VelocityEngine.
 */
public final class VelocityFactory {
  private static class Holder {
    private static final VelocityEngine engine = newVelocityEngine();
  }

  /**
   * Privte constructor.
   */
  private VelocityFactory() {
  }

  /**
   * Returns a new instance of the VelocityEngine.
   * <p/>
   * The engine is initialized and outputs its logging to IDEA logging.
   *
   * @return a new velocity engine that is initialized.
   */
  private static VelocityEngine newVelocityEngine() {
    ExtendedProperties prop = new ExtendedProperties();
    prop.addProperty(RuntimeConstants.RUNTIME_LOG_LOGSYSTEM_CLASS, JdkLogChute.class.getName());
    prop.addProperty(JdkLogChute.RUNTIME_LOG_JDK_LOGGER, "GenerateToString");
    prop.addProperty(RuntimeConstants.RESOURCE_LOADER, "includes");
    prop.addProperty("includes.resource.loader.class", VelocityIncludesClassLoader.class.getName());
    prop.addProperty(RuntimeConstants.VM_PERM_ALLOW_INLINE_REPLACE_GLOBAL, "true");

    VelocityEngine velocity = new VelocityEngine();
    velocity.setExtendedProperties(prop);
    ClassLoaderUtil.runWithClassLoader(VelocityIncludesClassLoader.class.getClassLoader(), () -> velocity.init());
    return velocity;
  }

  /**
   * Get's a shared instance of the VelocityEngine.
   * <p/>
   * The engine is initialized and outputs its logging to IDEA logging.
   *
   * @return a shared instance of the engine that is initialized.
   */
  public static VelocityEngine getVelocityEngine() {
    return Holder.engine;
  }
}