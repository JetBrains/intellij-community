load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "IntelliLang",
    srcs = ["src"],
    iml_files = ["IntelliLang.iml"],
    javacopts = ["-Xep:MissingCasesInEnumSwitch:WARN"],
    tags = ["managed"],
    # do not sort: must match IML order
    test_runtime_deps = [
        "//tools/idea:xml-structure-view-impl",
        "//tools/idea:spellchecker",
        "//tools/idea:images",
    ],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    runtime_deps = [
        "//tools/idea:xml-analysis-impl",
        "//tools/idea:xdebugger-impl",
        "//tools/idea:lvcs-impl",
        "//tools/idea:credential-store",
        "//tools/idea:configuration-store-impl",
        "//tools/idea:built-in-server",
        "//tools/idea:tasks-platform-impl",
        "//tools/idea:json",
        "//tools/idea:relaxng",
        "//tools/idea:dom-impl",
    ],
    # do not sort: must match IML order
    deps = [
        "//tools/idea:platform-impl[module]",
        "//tools/idea:lang-impl[module]",
        "//tools/idea:RegExpSupport[module]",
        "//tools/idea/platform/platform-api[module]",
        "//tools/idea/.idea/libraries:Jaxen",
    ],
)

iml_module(
    name = "IntelliLang-java",
    srcs = ["java-support"],
    iml_files = ["IntelliLang-java.iml"],
    tags = ["managed"],
    # do not sort: must match IML order
    test_runtime_deps = [
        "//tools/idea:execution-impl",
        "//tools/idea:external-system-impl",
    ],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    runtime_deps = [
        "//tools/idea:xdebugger-impl",
        "//tools/idea:lvcs-impl",
        "//tools/idea:credential-store",
        "//tools/idea:configuration-store-impl",
        "//tools/idea:tasks-platform-impl",
        "//tools/idea:json",
        "//tools/idea:relaxng",
        "//tools/idea/java/compiler/instrumentation-util-8",
    ],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/java/openapi[module]",
        "//tools/idea:platform-impl[module]",
        "//tools/idea:lang-impl[module]",
        "//tools/idea:jps-builders_and_others[module]",
        "//tools/idea/.idea/libraries:ASM",
        "//tools/idea:RegExpSupport[module]",
        "//tools/idea/platform/platform-api[module]",
        "//tools/idea:java-impl[module]",
        "//tools/idea/plugins/IntelliLang[module]",
    ],
)

iml_module(
    name = "IntelliLang-xml",
    srcs = ["xml-support"],
    iml_files = ["IntelliLang-xml.iml"],
    tags = ["managed"],
    # do not sort: must match IML order
    test_runtime_deps = [
        "//tools/idea:spellchecker",
        "//tools/idea:images",
    ],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    runtime_deps = [
        "//tools/idea:xdebugger-impl",
        "//tools/idea:credential-store",
        "//tools/idea:configuration-store-impl",
        "//tools/idea:built-in-server",
        "//tools/idea:tasks-platform-impl",
        "//tools/idea:json",
        "//tools/idea:relaxng",
        "//tools/idea:dom-impl",
    ],
    # do not sort: must match IML order
    deps = [
        "//tools/idea:platform-impl[module]",
        "//tools/idea:lang-impl[module]",
        "//tools/idea:xml[module]",
        "//tools/idea/.idea/libraries:ASM",
        "//tools/idea:RegExpSupport[module]",
        "//tools/idea/plugins/xpath[module]",
        "//tools/idea/platform/platform-api[module]",
        "//tools/idea/.idea/libraries:Jaxen",
        "//tools/idea/plugins/IntelliLang[module]",
    ],
)
