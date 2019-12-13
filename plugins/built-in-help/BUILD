load("//tools/base/bazel:bazel.bzl", "iml_module")

# managed by go/iml_to_build
iml_module(
    name = "intellij.platform.builtInHelp",
    srcs = ["src"],
    iml_files = ["intellij.platform.builtInHelp.iml"],
    # do not sort: must match IML order
    test_runtime_deps = [
        "//tools/idea/xml/dom-openapi:intellij.xml.dom",
        "//tools/idea/xml/dom-impl:intellij.xml.dom.impl",
        "//tools/idea/platform/testFramework/extensions:intellij.platform.testExtensions",
        "//tools/idea/images:intellij.platform.images",
    ],
    visibility = ["//visibility:public"],
    # do not sort: must match IML order
    runtime_deps = [
        "//tools/idea/platform/lvcs-impl:intellij.platform.lvcs.impl",
        "//tools/idea/platform/testRunner:intellij.platform.testRunner",
        "//tools/idea/spellchecker:intellij.spellchecker",
        "//tools/idea/platform/tasks-platform-impl:intellij.platform.tasks.impl",
        "//tools/idea/json:intellij.json",
        "//tools/idea/platform/credential-store:intellij.platform.credentialStore",
        "//tools/idea/platform/external-system-impl:intellij.platform.externalSystem.impl",
        "//tools/idea/platform/diagnostic:intellij.platform.diagnostic",
    ],
    # do not sort: must match IML order
    deps = [
        "//tools/idea/.idea/libraries:kotlin-stdlib-jdk8",
        "//tools/idea/.idea/libraries:netty-codec-http",
        "//prebuilts/tools/common/m2/repository/org/apache/lucene/lucene-core/5.0.0:jar",
        "//prebuilts/tools/common/m2/repository/org/apache/lucene/lucene-analyzers-common/5.0.0:jar",
        "//prebuilts/tools/common/m2/repository/org/apache/lucene/lucene-queries/5.0.0:jar",
        "//prebuilts/tools/common/m2/repository/org/apache/lucene/lucene-highlighter/5.0.0:jar",
        "//prebuilts/tools/common/m2/repository/org/apache/lucene/lucene-join/5.0.0:jar",
        "//prebuilts/tools/common/m2/repository/org/apache/lucene/lucene-memory/5.0.0:jar",
        "//prebuilts/tools/common/m2/repository/org/apache/lucene/lucene-queryparser/5.0.0:jar",
        "//prebuilts/tools/common/m2/repository/org/apache/lucene/lucene-sandbox/5.0.0:jar",
        "//prebuilts/tools/common/m2/repository/org/apache/lucene/lucene-codecs/5.0.0:jar",
        "//prebuilts/tools/common/m2/repository/org/apache/lucene/lucene-misc/5.0.0:jar",
        "//prebuilts/tools/common/m2/repository/org/apache/lucene/lucene-suggest/5.0.0:jar",
        "//tools/idea/.idea/libraries:gson",
        "//tools/idea/.idea/libraries:commons-compress",
        "//tools/idea/.idea/libraries:jsoup",
        "//tools/idea/platform/platform-api:intellij.platform.ide[module]",
        "//tools/idea/platform/built-in-server:intellij.platform.builtInServer.impl[module]",
        "//tools/idea/xml/impl:intellij.xml.impl[module]",
        "//tools/idea/platform/core-impl:intellij.platform.core.impl[module]",
        "//tools/idea/platform/util:intellij.platform.util[module]",
    ],
)
