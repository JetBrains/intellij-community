<?php

$info = array(
    "method" => $_SERVER["REQUEST_METHOD"],
    "params" => $_GET,
    "headers" => getallheaders(),
    "cookie" => $_COOKIE,
    "body" => gzdecode(file_get_contents('php://input'))
);

echo json_encode($info);