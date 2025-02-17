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

internal fun putColumnsOrder(schema: DataFrameSchema, o<caret>rder: MutableMap<ColumnPath, Int>, path: ColumnPath) {
    schema.columns.entries.forEachIndexed { i, (name, column) ->
        val columnPath = path + name
        order[columnPath] = i
        when (column) {
            is ColumnSchema.Frame -> {
                putColumnsOrder(column.schema, order, columnPath)
            }

            is ColumnSchema.Group -> {
                putColumnsOrder(column.schema, order, columnPath)
            }
        }
    }
}
// IGNORE_K1