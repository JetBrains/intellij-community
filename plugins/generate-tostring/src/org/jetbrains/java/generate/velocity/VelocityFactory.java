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
import org.apache.velocity.app.VelocityEngine;
import org.apache.velocity.runtime.RuntimeConstants;

/**
 * Velocity factory.
 * <p/>
 * Creating instances of the VelocityEngine.
 */
public class VelocityFactory {
  private static class Holder {
    private static final VelocityEngine engine = newVelocityEngine();
  }

  /**
   * Returns a new instance of the VelocityEngine.
   * <p/>
   * The engine is initialized and outputs its logging to IDEA logging.
   *
   * @return a new velocity engine that is initialized.
   */
  private static VelocityEngine newVelocityEngine() {
    VelocityEngine velocity = new VelocityEngine();
    velocity.setProperty(RuntimeConstants.RUNTIME_LOG_NAME, "GenerateToString");
    velocity.setProperty(RuntimeConstants.RESOURCE_LOADER, "includes");
    velocity.setProperty("includes.resource.loader.class", VelocityIncludesClassLoader.class.getName());
    velocity.setProperty(RuntimeConstants.VM_PERM_ALLOW_INLINE_REPLACE_GLOBAL, "true");
    velocity.init();
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