load("//tools/base/bazel:bazel.bzl", "iml_module")

iml_module(
    name = "git4idea",
    # do not sort: must match IML order
    srcs = [
        "src",
        "gen",
    ],
    iml_files = ["git4idea.iml"],
    javacopts = ["-Xep:MissingCasesInEnumSwitch:WARN"],
    resources = ["resources"],
    tags = ["managed"],
    # do not sort: must match IML order
    test_runtime_deps = [
        "//tools/idea:testExtensions",
        "//tools/idea:lvcs-impl",
        "//tools/idea:json",
        "//tools/idea:execution-impl",
    ],
    test_srcs = ["tests"],
    test_tags = ["manual"],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    exports = [
        "//tools/idea/platform/dvcs-impl",
        "//tools/idea/platform/dvcs-api",
        "//tools/idea/.idea/libraries:cucumber-testing",
    ],
    # do not sort: must match IML order
    runtime_deps = [
        "//tools/idea:credential-store",
        "//tools/idea:configuration-store-impl",
    ],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/platform/vcs-api[module]",
        "//tools/idea/plugins/git4idea/lib:trilead-ssh2",
        "//tools/idea/.idea/libraries:XmlRPC",
        "//tools/idea:platform-impl[module]",
        "//tools/idea:vcs-impl[module]",
        "//tools/idea/platform/lang-api[module]",
        "//tools/idea/.idea/libraries:JUnit4[test]",
        "//tools/idea/platform/lvcs-api[module]",
        "//tools/idea:testFramework[module, test]",
        "//tools/idea/.idea/libraries:TestNG[test]",
        "//tools/idea/platform/platform-api[module]",
        "//tools/idea/.idea/libraries:Guava",
        "//tools/idea:lang-impl[module]",
        "//tools/idea/plugins/git4idea/rt:git4idea-rt[module]",
        "//tools/idea:images[module]",
        "//tools/idea/plugins/git4idea/lib/ini4j:ini4j-0.5.2-patched",
        "//tools/idea/.idea/libraries:Groovy[test]",
        "//tools/idea/platform/dvcs-impl[module]",
        "//tools/idea/.idea/libraries:cucumber-core[test]",
        "//tools/idea/.idea/libraries:JSch",
        "//tools/idea/platform/vcs-log/api:vcs-log-api[module]",
        "//tools/idea/platform/vcs-log/impl:vcs-log-impl[module]",
        "//tools/idea/platform/vcs-log/graph:vcs-log-graph[module]",
        "//tools/idea/platform/dvcs-api[module]",
        "//tools/idea:built-in-server[module, test]",
        "//tools/idea/platform/built-in-server-api[module]",
        "//tools/idea/.idea/libraries:mockito[test]",
        "//tools/idea/.idea/libraries:cucumber-java[test]",
        "//tools/idea/.idea/libraries:KotlinTest[test]",
        "//tools/idea/platform/vcs-tests[module, test]",
        "//tools/idea/platform/util[module]",
        "//tools/idea/.idea/libraries:cucumber-testing[test]",
        "//tools/idea/platform/testGuiFramework[module, test]",
    ],
)
