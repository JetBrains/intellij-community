/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package org.jetbrains.io;

import com.intellij.util.lang.UrlClassLoader;


public class PowerSupplyKit {

    static {
        UrlClassLoader.loadPlatformLibrary("MacNativeKit");
    }

    public static native void startListenPowerSupply (PowerSupplyKitCallback callback);
    private static native String[] getInfo ();

    public static boolean hasDiscreteCard() {
        String [] models =  getInfo();

        if (models.length > 1) {
            return true;
            //for (String model : models) {
            //    if (model.contains("Radeon")) return true;
            //}
        }

        return false;
    }
}
