plugins {
    kotlin("jvm")
}


tasks.register("podInstall")

/* This 'podImport' task mock should still be invoked */
tasks.register("podImport") {
    dependsOn("podInstall")
}
