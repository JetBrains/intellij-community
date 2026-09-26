package test;

import kotlinx.coroutines.CoroutineScope;

interface CustomAction {
    void customInvoke();
}

class Main {
    static void javaStaticWrapper(CustomAction action) {
        action.customInvoke();
    }
}