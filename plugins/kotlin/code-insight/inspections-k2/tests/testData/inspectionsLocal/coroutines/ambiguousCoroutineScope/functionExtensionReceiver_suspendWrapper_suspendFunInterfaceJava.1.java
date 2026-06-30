package test

import kotlinx.coroutines.CoroutineScope

interface CustomSuspendLambda {
    void customInvoke();
}

class Main {
    static void suspendWrapper(CustomSuspendLambda action) {
        action.customInvoke();
    }
}