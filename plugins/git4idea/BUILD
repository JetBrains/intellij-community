load("//tools/base/bazel:bazel.bzl", "iml_module")

# managed by go/iml_to_build
iml_module(
    name = "intellij.vcs.git",
    # do not sort: must match IML order
    srcs = [
        "src",
        "gen",
    ],
    iml_files = ["intellij.vcs.git.iml"],
    javacopts = ["-Xep:MissingCasesInEnumSwitch:WARN"],
    # do not sort: must match IML order
    resources = [
        "resources",
        "compatibilityResources",
    ],
    test_srcs = ["tests"],
    test_tags = ["manual"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    exports = [
        "//tools/idea/platform/dvcs-impl:intellij.platform.vcs.dvcs.impl",
        "//tools/idea/platform/dvcs-api:intellij.platform.vcs.dvcs",
        "//tools/idea/.idea/libraries:cucumber-testing",
    ],
    # do not sort: must match IML order
    runtime_deps = [
        "//tools/idea/platform/lvcs-impl:intellij.platform.lvcs.impl",
        "//tools/idea/xml/xml-structure-view-impl:intellij.xml.structureView.impl",
        "//tools/idea/xml/dom-impl:intellij.xml.dom.impl",
        "//tools/idea/spellchecker:intellij.spellchecker",
        "//tools/idea/xml/relaxng:intellij.relaxng",
        "//tools/idea/json:intellij.json",
        "//tools/idea/platform/credential-store:intellij.platform.credentialStore",
        "//tools/idea/platform/configuration-store-impl:intellij.platform.configurationStore.impl",
    ],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/platform/vcs-api:intellij.platform.vcs[module]",
        "//tools/idea/.idea/libraries:trilead-ssh2",
        "//tools/idea/.idea/libraries:XmlRPC",
        "//tools/idea/platform/platform-impl:intellij.platform.ide.impl[module]",
        "//tools/idea/platform/vcs-impl:intellij.platform.vcs.impl[module]",
        "//tools/idea/platform/lang-api:intellij.platform.lang[module]",
        "//tools/idea/.idea/libraries:JUnit4[test]",
        "//tools/idea/platform/lvcs-api:intellij.platform.lvcs[module]",
        "//tools/idea/platform/testFramework:intellij.platform.testFramework[module, test]",
        "//tools/idea/platform/platform-api:intellij.platform.ide[module]",
        "//tools/idea/.idea/libraries:Guava",
        "//tools/idea/platform/lang-impl:intellij.platform.lang.impl[module]",
        "//tools/idea/plugins/git4idea/rt:intellij.vcs.git.rt[module]",
        "//tools/idea/images:intellij.platform.images[module]",
        "//tools/idea/.idea/libraries:Groovy[test]",
        "//tools/idea/platform/dvcs-impl:intellij.platform.vcs.dvcs.impl[module]",
        "//tools/idea/.idea/libraries:cucumber-core[test]",
        "//tools/idea/.idea/libraries:JSch",
        "//tools/idea/platform/vcs-log/api:intellij.platform.vcs.log[module]",
        "//tools/idea/platform/vcs-log/impl:intellij.platform.vcs.log.impl[module]",
        "//tools/idea/platform/vcs-log/graph:intellij.platform.vcs.log.graph.impl[module]",
        "//tools/idea/platform/dvcs-api:intellij.platform.vcs.dvcs[module]",
        "//tools/idea/platform/built-in-server:intellij.platform.builtInServer.impl[module, test]",
        "//tools/idea/platform/built-in-server-api:intellij.platform.builtInServer[module]",
        "//tools/idea/.idea/libraries:mockito[test]",
        "//tools/idea/.idea/libraries:cucumber-java[test]",
        "//tools/idea/.idea/libraries:KotlinTest[test]",
        "//tools/idea/platform/vcs-tests:intellij.platform.vcs.tests[module, test]",
        "//tools/idea/platform/util:intellij.platform.util[module]",
        "//tools/idea/.idea/libraries:cucumber-testing[test]",
        "//tools/idea/.idea/libraries:ini4j",
        "//tools/idea/platform/tasks-platform-impl:intellij.platform.tasks.impl[module]",
        "//tools/idea/platform/tasks-platform-api:intellij.platform.tasks[module, test]",
        "//tools/idea/.idea/libraries:assertJ[test]",
        "//tools/idea/.idea/libraries:commons-codec",
    ],
)
