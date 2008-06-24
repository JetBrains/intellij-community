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
package org.jetbrains.generate.tostring.velocity;

import org.apache.commons.collections.ExtendedProperties;
import org.apache.velocity.app.VelocityEngine;
import org.apache.velocity.runtime.log.SimpleLog4JLogSystem;

/**
 * Velocity factory.
 * <p/>
 * Creating instances of the VelocityEngine.
 */
public class VelocityFactory {

    private static VelocityEngine engine;

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
     * @return  a new velocity engine that is initialized.
     * @throws Exception  error creating the VelocityEngine.
     */
    public static VelocityEngine newVeloictyEngine() throws Exception {
        ExtendedProperties prop = new ExtendedProperties();
        prop.addProperty(VelocityEngine.RUNTIME_LOG_LOGSYSTEM_CLASS, SimpleLog4JLogSystem.class.getName());
        prop.addProperty("runtime.log.logsystem.log4j.category", "GenerateToString");
        VelocityEngine velocity = new VelocityEngine();
        velocity.setExtendedProperties(prop);
        velocity.init();
        return velocity;
    }

    /**
     * Get's a shared instance of the VelocityEngine.
     * <p/>
     * The engine is initialized and outputs its logging to IDEA logging.
     * @return  a shared instance of the engine that is initialized.
     * @throws Exception  error creating the VelocityEngine.
     */
    public static VelocityEngine getVelocityEngine() throws Exception {
        if (engine == null) {
            engine = newVeloictyEngine();
        }

        return engine;
    }

}