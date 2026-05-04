// swift-tools-version: 5.9
import PackageDescription

let package = Package(
    name: "localPackage",
    products: [
        .library(
            name: "localPackage",
            targets: ["localPackage"]
        ),
    ],
    targets: [
        .target(
            name: "localPackage"
        ),
    ]
)
