fun interface CommonMain {
    /*
    See: SuspendInFunInterfaceChecker
    This previously fired as common source sets were not setting the 'useIR' analysis flag
     */
    suspend fun thisFunctionRequiresIRBackend()
}