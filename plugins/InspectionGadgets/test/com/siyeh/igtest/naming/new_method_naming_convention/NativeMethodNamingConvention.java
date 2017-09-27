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
package com.siyeh.igtest.naming.native_method_naming_convention;

public class NativeMethodNamingConvention implements Runnable
{
    public native void <warning descr="'native' method name 'UpperaseMethod' doesn't match regex '[a-z][A-Za-z\d]*'">UpperaseMethod</warning>();

    public native void methodNameEndingIn2();

    public native void <warning descr="'native' method name 'foo' is too short (3 < 4)">foo</warning>();

    public native void <warning descr="'native' method name 'methodNameTooLoooooooooooooooooooooooooooooooooooooooooooooong' is too long (62 > 32)">methodNameTooLoooooooooooooooooooooooooooooooooooooooooooooong</warning>();

    public native void run();

    private void a() {}

    public static native void <warning descr="'native' method name 'b' is too short (1 < 4)">b</warning>();
}
