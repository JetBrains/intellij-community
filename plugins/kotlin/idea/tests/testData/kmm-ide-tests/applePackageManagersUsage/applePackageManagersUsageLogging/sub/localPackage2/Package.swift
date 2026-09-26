// swift-tools-version: 5.9
import PackageDescription

let package = Package(
    name: "localPackage2",
    products: [
        .library(
            name: "localPackage2",
            targets: ["localPackage2"]
        ),
    ],
    targets: [
        .target(
            name: "localPackage2"
        ),
    ]
)
