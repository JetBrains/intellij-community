import inspections.wrapInSupplierFix.MyService

fun main() {
    MyService.companionObjectAppServiceSupplier1.get().foo()
}