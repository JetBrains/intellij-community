
interface DataFrameSchema {
    val columns: Map<String, ColumnSchema>
}

abstract class ColumnSchema {
    class Group(val schema: DataFrameSchema) : ColumnSchema()

    class Frame(val schema: DataFrameSchema) : ColumnSchema()
}

data class ColumnPath(val path: List<String>) {
    operator fun plus(name: String): ColumnPath = ColumnPath(path + name)
}

internal fun Data<caret>FrameSchema.putColumnsOrder(order: MutableMap<ColumnPath, Int>, path: ColumnPath) {
    columns.entries.forEachIndexed { i, (name, column) ->
        val columnPath = path + name
        order[columnPath] = i
        when (column) {
            is ColumnSchema.Frame -> {
                column.schema.putColumnsOrder(order, columnPath)
            }

            is ColumnSchema.Group -> {
                column.schema.putColumnsOrder(order, columnPath)
            }
        }
    }
}
// IGNORE_K1