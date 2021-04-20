// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package pkg;

public class TestGenericWildcardFunctionReturnType {

    public static <T> T seeXenoAmess(A<? extends T> xenoAmess) {
        return xenoAmess instanceof B ? ((B<? extends T>)xenoAmess).bGlitch() : xenoAmess.aGlitch();
    }

    static class A<T>{
        public T aGlitch(){
            return null;
        }
    }

    static class B<T> extends A<T>{
        public T bGlitch(){
            return null;
        }
    }
}
