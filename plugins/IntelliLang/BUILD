load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "IntelliLang",
    srcs = ["src"],
    iml_files = ["IntelliLang.iml"],
    javacopts = ["-Xep:MissingCasesInEnumSwitch:WARN"],
    tags = ["managed"],
    # do not sort: must match IML order
    test_runtime_deps = [
        "//tools/idea/xml/xml-structure-view-impl",
        "//tools/idea/spellchecker",
        "//tools/idea/images",
    ],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    runtime_deps = [
        "//tools/idea/xml/xml-analysis-impl",
        "//tools/idea/platform/xdebugger-impl",
        "//tools/idea/platform/lvcs-impl",
        "//tools/idea/platform/credential-store",
        "//tools/idea/platform/configuration-store-impl",
        "//tools/idea/platform/built-in-server",
        "//tools/idea/platform/tasks-platform-impl",
        "//tools/idea/json",
        "//tools/idea/xml/relaxng",
        "//tools/idea/xml/dom-impl",
    ],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/platform/platform-impl[module]",
        "//tools/idea/platform/lang-impl[module]",
        "//tools/idea/RegExpSupport[module]",
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
        "//tools/idea/java/execution/impl:execution-impl",
        "//tools/idea/platform/external-system-impl",
    ],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    runtime_deps = [
        "//tools/idea/platform/xdebugger-impl",
        "//tools/idea/platform/lvcs-impl",
        "//tools/idea/platform/credential-store",
        "//tools/idea/platform/configuration-store-impl",
        "//tools/idea/platform/tasks-platform-impl",
        "//tools/idea/json",
        "//tools/idea/xml/relaxng",
        "//tools/idea/java/compiler/instrumentation-util-8",
    ],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/java/openapi[module]",
        "//tools/idea/platform/platform-impl[module]",
        "//tools/idea/platform/lang-impl[module]",
        "//tools/idea:jps-builders_and_others[module]",
        "//tools/idea/.idea/libraries:ASM",
        "//tools/idea/RegExpSupport[module]",
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
        "//tools/idea/spellchecker",
        "//tools/idea/images",
    ],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    runtime_deps = [
        "//tools/idea/platform/xdebugger-impl",
        "//tools/idea/platform/credential-store",
        "//tools/idea/platform/configuration-store-impl",
        "//tools/idea/platform/built-in-server",
        "//tools/idea/platform/tasks-platform-impl",
        "//tools/idea/json",
        "//tools/idea/xml/relaxng",
        "//tools/idea/xml/dom-impl",
    ],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/platform/platform-impl[module]",
        "//tools/idea/platform/lang-impl[module]",
        "//tools/idea/xml/impl:xml[module]",
        "//tools/idea/.idea/libraries:ASM",
        "//tools/idea/RegExpSupport[module]",
        "//tools/idea/plugins/xpath[module]",
        "//tools/idea/platform/platform-api[module]",
        "//tools/idea/.idea/libraries:Jaxen",
        "//tools/idea/plugins/IntelliLang[module]",
    ],
)
