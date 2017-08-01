load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "IntelliLang",
    srcs = ["src"],
    iml_files = ["IntelliLang.iml"],
    javacopts = ["-Xep:MissingCasesInEnumSwitch:WARN"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//tools/idea:platform-impl_and_others[module]",
        "//tools/idea/platform/platform-api[module]",
        "//tools/idea/.idea/libraries:Jaxen",
    ],
)

iml_module(
    name = "IntelliLang-java",
    srcs = ["java-support"],
    iml_files = ["IntelliLang-java.iml"],
    tags = ["managed"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/java/openapi[module]",
        "//tools/idea:platform-impl_and_others[module]",
        "//tools/idea:compiler-impl_and_others[module]",
        "//tools/idea/.idea/libraries:ASM",
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
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//tools/idea:platform-impl_and_others[module]",
        "//tools/idea/.idea/libraries:ASM",
        "//tools/idea/plugins/xpath[module]",
        "//tools/idea/platform/platform-api[module]",
        "//tools/idea/.idea/libraries:Jaxen",
        "//tools/idea/plugins/IntelliLang[module]",
    ],
)
