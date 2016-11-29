/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.siyeh.igtest.bugs.object_to_string;

import java.io.*;

class ObjectToString_IGNORE_EXCEPTION
{
    static class N {}

    public void testException(N n, int i) {
        assert i < 10 : "Invalid args: "+<warning descr="Call to default 'toString()' on 'n'">n</warning>+", "+i;
        if(i < 0) {
            throw new IllegalArgumentException("Supplied argument is invalid: "+n);
        }
        if(i < 1) {
            throw createException(n.toString());
        }
    }

    public RuntimeException createException(String msg) {
        return new RuntimeException(msg);
    }
}