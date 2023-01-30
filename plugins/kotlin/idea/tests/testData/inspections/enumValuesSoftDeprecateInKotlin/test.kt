package sample

import sample.LocalEnum
import sample.LocalEnum.values
import sample.LocalEnum as LocalEnumImportAlias
import sample.LocalEnum.values as localEnumValuesStaticImport

public enum class LocalEnum {
    ;
    companion object {
        fun values(arg: Boolean) {}
    }
}

class NotEnum {
    companion object {
        fun values(): Array<NotEnum> = emptyArray()
    }
}

typealias LocalEnumTypeAlias = sample.LocalEnum

@ExperimentalStdlibApi
fun foo() {
    // Must report
    LocalEnum.values()
    sample.LocalEnum.values()
    JavaEnum.values()
    LocalEnumImportAlias.values()
    LocalEnumTypeAlias.values()
    values()

    // Must not report
    NotEnum.values()
    LocalEnum.values(false)
    JavaEnum.values(false)
    localEnumValuesStaticImport()
}
