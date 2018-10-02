load("//tools/base/bazel:bazel.bzl", "iml_module")

# managed by go/iml_to_build
iml_module(
    name = "intellij.vcs.github",
    srcs = ["src"],
    iml_files = ["intellij.vcs.github.iml"],
    resources = ["resources"],
    test_srcs = ["test"],
    test_tags = ["manual"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    exports = [
        "//tools/idea/plugins/tasks/tasks-core:intellij.tasks.core",
        "//tools/idea/plugins/tasks/tasks-api:intellij.tasks",
    ],
    # do not sort: must match IML order
    runtime_deps = [
        "//tools/idea/platform/lvcs-impl:intellij.platform.lvcs.impl",
        "//tools/idea/xml/dom-impl:intellij.xml.dom.impl",
        "//tools/idea/spellchecker:intellij.spellchecker",
        "//tools/idea/xml/relaxng:intellij.relaxng",
        "//tools/idea/json:intellij.json",
        "//tools/idea/platform/configuration-store-impl:intellij.platform.configurationStore.impl",
        "//tools/idea/platform/built-in-server:intellij.platform.builtInServer.impl",
    ],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/platform/vcs-api:intellij.platform.vcs[module]",
        "//tools/idea/plugins/git4idea:intellij.vcs.git[module]",
        "//tools/idea/.idea/libraries:http-client",
        "//tools/idea/platform/vcs-impl:intellij.platform.vcs.impl[module]",
        "//tools/idea/plugins/tasks/tasks-core:intellij.tasks.core[module]",
        "//tools/idea/plugins/tasks/tasks-api:intellij.tasks[module]",
        "//tools/idea/platform/platform-impl:intellij.platform.ide.impl[module]",
        "//tools/idea/platform/util:intellij.platform.util[module]",
        "//tools/idea/.idea/libraries:gson",
        "//tools/idea/platform/dvcs-impl:intellij.platform.vcs.dvcs.impl[module]",
        "//tools/idea/platform/dvcs-api:intellij.platform.vcs.dvcs[module]",
        "//tools/idea/platform/testFramework:intellij.platform.testFramework[module, test]",
        "//tools/idea/xml/openapi:intellij.xml[module]",
        "//tools/idea/xml/impl:intellij.xml.impl[module]",
        "//tools/idea/platform/vcs-log/api:intellij.platform.vcs.log[module]",
        "//tools/idea/platform/vcs-log/impl:intellij.platform.vcs.log.impl[module]",
        "//tools/idea/plugins/tasks/tasks-core/jira:intellij.tasks.jira[module, test]",
        "//tools/idea/platform/vcs-tests:intellij.platform.vcs.tests[module, test]",
        "//tools/idea/.idea/libraries:Guava",
        "//tools/idea/platform/credential-store:intellij.platform.credentialStore[module]",
        "//tools/idea/platform/lang-impl:intellij.platform.lang.impl[module]",
    ],
)
