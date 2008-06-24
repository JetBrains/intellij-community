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
package org.jetbrains.generate.tostring;

import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.generate.tostring.config.Config;

/**
 * Application context for this plugin.
 */
public class GenerateToStringContext {
    private static final Logger log = Logger.getInstance("#org.jetbrains.generate.tostring.GenerateToStringContext"); 
    private static Config config;

    public static Config getConfig() {
        if (config == null) {
            log.warn("Config is null - return a new default Config");
            config = new Config();
        }
        return config;
    }

    public static void setConfig(Config newConfig) {
        config = newConfig;
    }

}
