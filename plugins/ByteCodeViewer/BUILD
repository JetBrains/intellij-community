load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "ByteCodeViewer",
    srcs = ["src"],
    iml_files = ["ByteCodeViewer.iml"],
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
        "//tools/idea/.idea/libraries:ASM",
        "//tools/idea/platform/util[module]",
        "//tools/idea/platform/core-api[module]",
        "//tools/idea/platform/platform-api[module]",
        "//tools/idea:platform-impl[module]",
        "//tools/idea/platform/lang-api[module]",
        "//tools/idea:lang-impl[module]",
        "//tools/idea/java/java-psi-api[module]",
        "//tools/idea/java/execution/openapi:execution-openapi[module]",
        "//tools/idea/java/openapi[module]",
        "//tools/idea:jps-builders_and_others[module]",
    ],
)
