def deleteOtherColumns(table, columns) {
    def newTable = []
    table.each {
        def index = 0;
        newTable << it.findAll {columns.contains(index++)}
    }
    return newTable
}