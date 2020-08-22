<?php

function paramOrDefault($name, $default) {
    if (isset($_GET[$name])) {
        return $_GET[$name];
    }
    return $default;
}

$time = paramOrDefault('last_modified_time', 1589968216);
header("Last-Modified: " . gmdate("D, d M Y H:i:s", $time) . " GMT");
header('Content-Type: application/json');

$productCode = paramOrDefault('product_code', 'IC');
readfile($productCode . ".json");
