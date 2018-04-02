load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "groovy-uast-tests",
    iml_files = ["groovy-uast-tests.iml"],
    tags = ["managed"],
    # do not sort: must match IML order
    test_runtime_deps = [
        "//tools/idea/platform/lvcs-impl",
        "//tools/idea/json",
        "//tools/idea/platform/tasks-platform-impl",
        "//tools/idea/platform/credential-store",
        "//tools/idea/platform/configuration-store-impl",
        "//tools/idea/platform/external-system-impl",
        "//tools/idea/java/compiler/instrumentation-util-8",
        "//tools/idea:java-tests",
        "//tools/idea/plugins/IntelliLang/intellilang-jps-plugin",
    ],
    test_srcs = ["test"],
    test_tags = ["manual"],  # fails after IDEA 181.2784.17 merge
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/java/openapi[module, test]",
        "//tools/idea/platform/lang-api[module, test]",
        "//tools/idea/plugins/groovy/groovy-psi[module, test]",
        "//tools/idea/uast/uast-tests[module, test]",
        "//tools/idea/platform/testFramework[module, test]",
        "//tools/idea:jps-builders_and_others[module, test]",
    ],
)
